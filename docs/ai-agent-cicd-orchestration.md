# AI Agent CI/CD Orchestration Design

## 1. 目标

本文档定义一套混合模式的 AI Agent CI/CD 自动修复体系。GitHub Actions 和 Jenkins 继续负责构建、测试、制品和部署流水线；独立的 AI Orchestrator 负责读取流水线失败结果、编排多个 coding agent、生成修复分支、提交修复 PR，并交由人工 Review 后合并。

第一版目标是把 Maven 编译失败和单元测试失败纳入自动诊断与修复流程，同时保留对 Codex CLI、Claude Code、Gemini CLI 等不同 coding agent 的兼容能力。

## 2. 总体架构

```text
GitHub Actions / Jenkins
  ↓
CI Event Collector
  ↓
Analyzer / Router
  ↓
Planner / Orchestrator
  ↓
Agent Runtime Adapter
  ├─ Codex CLI Adapter
  ├─ Claude Code Adapter
  ├─ Gemini CLI Adapter
  └─ Future Agent Adapter
  ↓
Worker Agents in isolated git worktrees
  ↓
Patch Merger
  ↓
Verification Runner
  ↓
Reviewer / Auditor Agent
  ↓
PR Creator
  ↓
Human Review
```

### 2.1 混合模式分工

| 模块 | 职责 |
|---|---|
| GitHub Actions | PR 检查、Maven 编译、单元测试、上传日志和测试报告 |
| Jenkins | 内网部署、测试环境发布、UAT/生产发布审批、部署失败日志上报 |
| AI Orchestrator | 失败事件接收、上下文收集、Agent 编排、patch 合并、验证、创建修复 PR |
| Coding Agent CLI | 在隔离 worktree 中分析问题、修改代码、补测试、输出结构化结果 |
| Human Review | 审查修复 PR，决定是否合并和发布 |

CI/CD 是执行系统，AI Orchestrator 是自动诊断和修复系统，二者通过事件、日志和制品交互，不互相侵入职责。

## 3. 技术选型

AI Orchestrator 建议作为独立 TypeScript 服务实现，不放入现有 Java 业务微服务。

选择 TypeScript 的原因：

1. 更适合调度 CLI 进程、处理 JSON、文件流和异步任务。
2. 更容易集成 GitHub API、Webhook、队列和 artifact 下载。
3. 不污染企业知识库业务服务边界。
4. 可以独立部署为研发基础设施服务。

现有 Java 微服务项目保持业务系统定位，不承担 Agent 编排职责。

## 4. Agent 标准角色

| 角色 | Agent 名称 | 职责 |
|---|---|---|
| Analyzer / Router | `ci-log-analyzer` | 读取 CI 失败日志，判断失败类型、影响模块和需要的后续 Agent |
| Planner / Orchestrator | `fix-orchestrator` | 拆解任务、分派 Agent、控制执行顺序、监控进度 |
| Worker / Executor | `context-agent` | 读取 `AGENTS.md`、`docs/AGENTS.md`、相关 `pom.xml`、核心代码和测试 |
| Worker / Executor | `build-fix-agent` | 处理 Maven 编译错误、依赖冲突、模块构建失败 |
| Worker / Executor | `test-analysis-agent` | 读取 Surefire 报告和测试日志，判断单测失败根因 |
| Worker / Executor | `code-fix-agent` | 根据分析结果修改业务代码 |
| Worker / Executor | `test-writer-agent` | 补充回归测试或修正过期测试 |
| Writer / Synthesizer | `pr-writer-agent` | 汇总失败原因、修复说明、影响范围和验证结果 |
| Reviewer / Auditor | `review-agent` | 审查最终 diff、测试结果、项目规范、安全边界和修改范围 |

测试分析类 Agent 默认只读，不直接修改代码。是否修改代码由 Orchestrator 根据分析结果分派给 `code-fix-agent` 或 `test-writer-agent`。

## 5. Agent Runtime Adapter

Orchestrator 不直接绑定某个 CLI，而是通过统一 Runtime Adapter 调用不同 coding agent。

```text
AI Orchestrator
  ↓
AgentTaskSpec / AgentResult
  ↓
AgentRuntimeAdapter
  ├─ CodexCliAdapter
  ├─ ClaudeCodeAdapter
  ├─ GeminiCliAdapter
  └─ CustomAgentAdapter
```

### 5.1 AgentTaskSpec

```json
{
  "taskId": "fix-unit-test-001",
  "role": "test-analysis-agent",
  "runtime": "codex",
  "workspace": "/worktrees/test-analysis",
  "inputArtifacts": {
    "ciLog": "artifacts/ci.log",
    "testReports": "artifacts/surefire-reports",
    "gitDiff": "artifacts/git.diff"
  },
  "repoContext": [
    "AGENTS.md",
    "docs/AGENTS.md",
    "pom.xml",
    "enterprise-knowledge-ai-service/pom.xml"
  ],
  "constraints": [
    "Do not modify protected branches",
    "Do not read production secrets",
    "Follow repository AGENTS.md rules",
    "Use Javadoc block comments only"
  ],
  "expectedOutput": {
    "type": "analysis-report",
    "format": "json"
  }
}
```

### 5.2 AgentResult

```json
{
  "agentName": "test-analysis-agent",
  "status": "success",
  "summary": "Unit test failed because the expected document lifecycle no longer matches the service behavior.",
  "rootCause": "The test expectation is stale after document status transition changes.",
  "changedFiles": [],
  "patchFile": null,
  "verificationCommand": "mvn test -pl enterprise-knowledge-ai-service",
  "riskLevel": "medium",
  "needsHumanReview": true
}
```

会修改代码的 Agent 必须额外返回 patch 文件路径、修改文件列表和建议验证命令。Orchestrator 只接受结构化结果，不从自由文本中猜测执行状态。

## 6. Worktree 隔离策略

多个 coding agent 不能同时修改同一个工作区。Orchestrator 为每个会执行任务的 Agent 创建独立 git worktree。

```text
/workspace/main
  Orchestrator 使用，负责合并和验证

/workspace/worktrees/context
  context-agent 使用，通常只读

/workspace/worktrees/build-fix
  build-fix-agent 使用

/workspace/worktrees/test-analysis
  test-analysis-agent 使用，通常只读

/workspace/worktrees/code-fix
  code-fix-agent 使用

/workspace/worktrees/test-writer
  test-writer-agent 使用

/workspace/worktrees/review
  review-agent 使用，只读审查最终 diff
```

每个 Worker Agent 只能在自己的 worktree 内读写文件。最终 patch 统一交给 Patch Merger 处理，避免多个 Agent 互相覆盖。

## 7. CI 失败事件格式

GitHub Actions 或 Jenkins 失败后，需要把关键信息发送给 Orchestrator。

```json
{
  "eventId": "ci-20260529-0001",
  "provider": "github-actions",
  "repository": "EnterpriseKnowledgeWorkspace",
  "branch": "feature/example",
  "commitSha": "abc123",
  "pullRequest": 42,
  "workflow": "maven-ci",
  "job": "test",
  "status": "failure",
  "artifacts": {
    "ciLog": "https://artifact.example/ci.log",
    "surefireReports": "https://artifact.example/surefire.zip",
    "gitDiff": "https://artifact.example/git.diff",
    "moduleInfo": "https://artifact.example/module-info.json"
  },
  "triggeredAt": "2026-05-29T10:00:00+08:00"
}
```

事件中不包含明文密钥。Orchestrator 通过受限 Token 拉取 artifacts。

## 8. 第一版执行流程

```text
1. Pull Request 或分支提交触发 CI。
2. GitHub Actions 执行 Maven 编译和单元测试。
3. CI 失败后上传 ci.log、surefire-reports、git.diff、module-info。
4. CI 通过 webhook 通知 AI Orchestrator。
5. Analyzer / Router 判断失败类型。
6. Planner / Orchestrator 创建 AgentTaskSpec。
7. Worktree Manager 为每个 Agent 创建隔离 worktree。
8. Context Agent 收集相关项目上下文。
9. Build Fix Agent 或 Test Analysis Agent 分析失败。
10. Code Fix Agent 和 Test Writer Agent 按需生成 patch。
11. Patch Merger 合并候选 patch 到修复分支。
12. Verification Runner 执行模块级 Maven 测试。
13. Reviewer / Auditor Agent 审查最终 diff 和测试结果。
14. PR Creator 创建修复 PR。
15. 人工 Review 后合并。
```

## 9. MVP 范围

第一版支持：

1. GitHub Actions 失败事件接入。
2. Jenkins 失败事件格式预留。
3. Maven 编译失败分析。
4. Maven 单元测试失败分析。
5. Surefire 测试报告读取。
6. 多 Agent 分工执行。
7. 独立 git worktree 隔离。
8. Codex CLI Adapter 首个实现。
9. Claude Code 和 Gemini CLI Adapter 接口预留。
10. 自动创建修复分支和修复 PR。
11. PR 描述自动生成。
12. 人工 Review 后合并。

第一版不支持：

1. 自动合并 `main` 或 `master`。
2. 自动发布 TEST、UAT 或 PROD。
3. 自动修改生产配置。
4. 自动读取生产密钥。
5. 自动处理数据库迁移失败。
6. 自动处理复杂架构重构。
7. 自动删除文件、数据库或外部资源。
8. 自动绕过测试、扫描或人工审批。

## 10. Patch 合并规则

Patch Merger 必须遵守以下规则：

1. 只合并来自受信 AgentResult 的 patch。
2. patch 必须能干净应用到修复分支。
3. patch 修改范围必须匹配 AgentTaskSpec 中声明的任务边界。
4. 同一文件存在多个 patch 时，由 Orchestrator 标记冲突并交给 Reviewer 或人工处理。
5. patch 不允许修改 CI 凭据、生产配置、密钥文件和保护分支规则。
6. patch 合并后必须执行验证命令。
7. 验证失败时，Orchestrator 最多允许有限次数返工，超过次数后创建诊断报告而不是继续自动修改。

## 11. 验证策略

Verification Runner 先执行最小验证，再按风险扩大验证范围。

| 场景 | 验证命令 |
|---|---|
| 单模块编译失败 | `mvn test -pl <module>` |
| 涉及 shared frameworks | `mvn test` |
| 涉及多个业务模块 | `mvn test` |
| 只修改测试 | `mvn test -pl <module> -Dtest=<TestClass>` 后执行模块测试 |

本项目已有 Maven 统一入口，第一版默认使用 Java 17 和 Maven 执行验证。

## 12. Review 审核规则

Reviewer / Auditor Agent 需要检查：

1. 是否遵守根目录 `AGENTS.md` 和 `docs/AGENTS.md`。
2. 是否出现硬编码账号、密码、Token、私钥。
3. 是否绕过权限校验。
4. 是否泄露无权限文档标题、摘要、片段或来源。
5. 是否修改了与失败无关的大范围代码。
6. 是否把共享代码复制到业务模块。
7. 是否违反 Javadoc-only 注释规则。
8. 是否新增了缺少测试的高风险行为。
9. 是否存在只改测试掩盖真实业务缺陷的情况。
10. 是否保留了可复现的验证命令和结果。

Reviewer 可以给出通过、返工或升级人工处理三种结论。

## 13. 权限和安全边界

Agent 可以：

1. 读取 CI 日志和测试报告。
2. 读取仓库代码和文档。
3. 在非保护分支创建修复分支。
4. 修改修复分支中的代码和测试。
5. 创建修复 PR。
6. 触发 CI 复跑。
7. 生成修复说明和风险说明。

Agent 不可以：

1. 合并 `main`、`master` 或其他保护分支。
2. 发布 TEST、UAT 或 PROD。
3. 修改 GitHub、Jenkins、Harbor、Nexus 等平台凭据。
4. 读取或输出明文生产密钥。
5. 删除数据库、镜像仓库、制品仓库或生产资源。
6. 跳过测试、扫描或人工审批。
7. 修改生产配置文件。
8. 绕过权限、审计、安全扫描或 Review 规则。

所有 Agent 操作必须记录审计日志，包括任务输入、使用的 runtime、修改文件、验证命令、验证结果和最终 PR 链接。

## 14. 与当前项目的集成点

当前仓库是 Java 17 + Maven 多模块项目。第一版 CI 重点接入以下命令：

```bash
mvn test
mvn test -pl enterprise-knowledge-ai-service
mvn test -pl enterprise-gateway-service
mvn clean package -DskipTests
```

CI artifact 建议包含：

```text
artifacts/ci.log
artifacts/surefire-reports/
artifacts/git.diff
artifacts/module-info.json
artifacts/dependency-tree.txt
```

Agent 首先读取：

```text
AGENTS.md
docs/AGENTS.md
docs/api.md
docs/database.md
docs/deployment.md
docs/step3-summary.md
pom.xml
相关模块 pom.xml
相关失败测试和业务代码
```

## 15. 后续扩展

第一版稳定后，可以逐步扩展：

1. 接入 SonarQube、SpotBugs、Checkstyle 和依赖漏洞扫描。
2. 支持 Docker 镜像构建失败诊断。
3. 支持 Jenkins 部署失败日志分析。
4. 支持自动生成 release notes。
5. 支持 UAT 发布前检查清单。
6. 支持多 runtime 成本和效果评分。
7. 支持按 Agent 历史成功率动态选择 Codex、Claude Code 或 Gemini CLI。
8. 支持企业内部知识库作为 Agent 项目上下文来源。

## 16. 设计结论

本方案采用 TypeScript 独立 Orchestrator、统一 Agent 协议、Runtime Adapter、多 worktree 隔离和人工 Review 兜底。第一版先解决 Maven 编译失败和单元测试失败，既能验证多 Agent 协同价值，又能控制自动修改代码的风险。

该设计的关键约束是：AI Agent 可以诊断、修复、补测试和创建 PR，但不能直接合并保护分支，不能发布生产，不能接触生产密钥，不能绕过测试和人工审批。
