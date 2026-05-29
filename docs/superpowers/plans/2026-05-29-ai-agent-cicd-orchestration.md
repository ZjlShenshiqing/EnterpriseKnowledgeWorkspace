# AI Agent CI/CD Orchestration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a TypeScript AI Orchestrator MVP that receives CI failure events, classifies Maven build/test failures, plans multi-agent repair tasks, runs compatible CLI adapters in isolated worktrees, collects structured results, verifies patches, and prepares repair PR output.

**Architecture:** Add a standalone `ai-orchestrator/` Node.js TypeScript service outside the Java microservices. The service uses typed domain models, runtime adapters for coding CLIs, worktree isolation, artifact readers, a deterministic planner, and a local dry-run PR creator for MVP safety. GitHub Actions produces CI artifacts and can later call the Orchestrator webhook.

**Tech Stack:** Node.js 20, TypeScript, Vitest, Zod, Fastify, execa, simple-git, GitHub Actions, Maven.

---

## File Structure

Create these files:

```text
ai-orchestrator/
  package.json
  tsconfig.json
  vitest.config.ts
  README.md
  .env.example
  examples/
    github-actions-failure-event.json
    surefire-failure-report.xml
  src/
    index.ts
    server.ts
    config.ts
    domain/
      agent-result.ts
      agent-task-spec.ts
      ci-event.ts
      orchestration-run.ts
    adapters/
      agent-runtime-adapter.ts
      codex-cli-adapter.ts
      mock-agent-adapter.ts
    artifacts/
      artifact-loader.ts
      surefire-parser.ts
    analyzer/
      ci-log-analyzer.ts
    planner/
      task-planner.ts
    worktree/
      worktree-manager.ts
    patch/
      patch-merger.ts
    verification/
      verification-runner.ts
    review/
      review-gate.ts
    pr/
      pr-creator.ts
    orchestrator.ts
    utils/
      command-runner.ts
  tests/
    ci-log-analyzer.test.ts
    surefire-parser.test.ts
    task-planner.test.ts
    orchestrator.test.ts
.github/
  workflows/
    maven-ci.yml
```

Responsibilities:

| Path | Responsibility |
|---|---|
| `src/domain/*` | Shared Zod schemas and TypeScript types for events, tasks, results, and runs |
| `src/artifacts/*` | Load local artifact files and parse Surefire XML |
| `src/analyzer/ci-log-analyzer.ts` | Classify CI failure type and affected Maven module |
| `src/planner/task-planner.ts` | Convert analysis into ordered multi-agent tasks |
| `src/adapters/*` | Hide differences between Codex CLI, future CLI runtimes, and test doubles |
| `src/worktree/worktree-manager.ts` | Create isolated git worktrees for writable agents |
| `src/patch/patch-merger.ts` | Apply trusted patch files into a repair branch |
| `src/verification/verification-runner.ts` | Run Maven verification commands |
| `src/review/review-gate.ts` | Enforce MVP safety rules before PR creation |
| `src/pr/pr-creator.ts` | MVP dry-run PR creator that writes a PR summary artifact |
| `.github/workflows/maven-ci.yml` | CI workflow that runs Maven tests and uploads logs/reports |

---

### Task 1: Scaffold TypeScript Orchestrator Project

**Files:**
- Create: `ai-orchestrator/package.json`
- Create: `ai-orchestrator/tsconfig.json`
- Create: `ai-orchestrator/vitest.config.ts`
- Create: `ai-orchestrator/.env.example`
- Create: `ai-orchestrator/README.md`

- [ ] **Step 1: Create `package.json`**

```json
{
  "name": "enterprise-ai-orchestrator",
  "version": "0.1.0",
  "private": true,
  "type": "module",
  "scripts": {
    "build": "tsc -p tsconfig.json",
    "test": "vitest run",
    "test:watch": "vitest",
    "dev": "tsx src/index.ts",
    "start": "node dist/index.js",
    "lint": "tsc -p tsconfig.json --noEmit"
  },
  "engines": {
    "node": ">=20"
  },
  "dependencies": {
    "@fastify/sensible": "^5.6.0",
    "@octokit/rest": "^21.1.1",
    "execa": "^9.5.2",
    "fastify": "^5.2.1",
    "fast-xml-parser": "^4.5.1",
    "simple-git": "^3.27.0",
    "zod": "^3.24.1"
  },
  "devDependencies": {
    "@types/node": "^22.10.5",
    "tsx": "^4.19.2",
    "typescript": "^5.7.2",
    "vitest": "^2.1.8"
  }
}
```

- [ ] **Step 2: Create `tsconfig.json`**

```json
{
  "compilerOptions": {
    "target": "ES2022",
    "module": "NodeNext",
    "moduleResolution": "NodeNext",
    "strict": true,
    "esModuleInterop": true,
    "forceConsistentCasingInFileNames": true,
    "skipLibCheck": true,
    "outDir": "dist",
    "rootDir": "src",
    "types": ["node"]
  },
  "include": ["src/**/*.ts"],
  "exclude": ["dist", "node_modules", "tests"]
}
```

- [ ] **Step 3: Create `vitest.config.ts`**

```ts
import { defineConfig } from "vitest/config";

export default defineConfig({
  test: {
    environment: "node",
    include: ["tests/**/*.test.ts"]
  }
});
```

- [ ] **Step 4: Create `.env.example`**

```dotenv
ORCHESTRATOR_PORT=8095
ORCHESTRATOR_WORKSPACE_ROOT=/tmp/enterprise-ai-orchestrator
ORCHESTRATOR_DEFAULT_RUNTIME=mock
ORCHESTRATOR_MAX_REPAIR_ATTEMPTS=2
GITHUB_TOKEN=
GITHUB_OWNER=
GITHUB_REPO=
CODEX_CLI_COMMAND=codex
```

- [ ] **Step 5: Create `README.md`**

```md
# AI Orchestrator

Standalone TypeScript service for CI/CD AI Agent orchestration.

## MVP Scope

- Receive CI failure events.
- Classify Maven compile and unit-test failures.
- Plan multi-agent tasks.
- Run agents through runtime adapters.
- Keep writable agents isolated in git worktrees.
- Verify repair output before PR creation.

## Commands

```bash
npm install
npm test
npm run build
npm run dev
```

## Safety

The Orchestrator may create repair branches and PR summaries. It must not merge protected branches, deploy environments, read production secrets, or bypass CI.
```

- [ ] **Step 6: Run install and baseline test command**

Run:

```bash
cd ai-orchestrator
npm install
npm test
```

Expected:

```text
No test files found
```

or Vitest exits non-zero because no tests exist yet. This is acceptable only for this scaffold task.

- [ ] **Step 7: Commit scaffold**

```bash
git add ai-orchestrator/package.json ai-orchestrator/tsconfig.json ai-orchestrator/vitest.config.ts ai-orchestrator/.env.example ai-orchestrator/README.md
git commit -m "feat: scaffold ai orchestrator"
```

---

### Task 2: Define Domain Schemas

**Files:**
- Create: `ai-orchestrator/src/domain/ci-event.ts`
- Create: `ai-orchestrator/src/domain/agent-task-spec.ts`
- Create: `ai-orchestrator/src/domain/agent-result.ts`
- Create: `ai-orchestrator/src/domain/orchestration-run.ts`

- [ ] **Step 1: Create CI event schema**

Create `ai-orchestrator/src/domain/ci-event.ts`:

```ts
import { z } from "zod";

export const CiProviderSchema = z.enum(["github-actions", "jenkins"]);

export const CiFailureEventSchema = z.object({
  eventId: z.string().min(1),
  provider: CiProviderSchema,
  repository: z.string().min(1),
  branch: z.string().min(1),
  commitSha: z.string().min(1),
  pullRequest: z.number().int().positive().optional(),
  workflow: z.string().min(1),
  job: z.string().min(1),
  status: z.literal("failure"),
  artifacts: z.object({
    ciLog: z.string().min(1),
    surefireReports: z.string().min(1).optional(),
    gitDiff: z.string().min(1).optional(),
    moduleInfo: z.string().min(1).optional(),
    dependencyTree: z.string().min(1).optional()
  }),
  triggeredAt: z.string().datetime({ offset: true })
});

export type CiFailureEvent = z.infer<typeof CiFailureEventSchema>;
```

- [ ] **Step 2: Create Agent task schema**

Create `ai-orchestrator/src/domain/agent-task-spec.ts`:

```ts
import { z } from "zod";

export const AgentRoleSchema = z.enum([
  "ci-log-analyzer",
  "context-agent",
  "build-fix-agent",
  "test-analysis-agent",
  "code-fix-agent",
  "test-writer-agent",
  "pr-writer-agent",
  "review-agent"
]);

export const AgentRuntimeSchema = z.enum(["mock", "codex", "claude", "gemini", "custom"]);

export const ExpectedOutputSchema = z.object({
  type: z.enum(["analysis-report", "patch", "review", "pr-summary"]),
  format: z.enum(["json", "markdown"])
});

export const AgentTaskSpecSchema = z.object({
  taskId: z.string().min(1),
  role: AgentRoleSchema,
  runtime: AgentRuntimeSchema,
  workspace: z.string().min(1),
  inputArtifacts: z.record(z.string()),
  repoContext: z.array(z.string()),
  constraints: z.array(z.string()),
  expectedOutput: ExpectedOutputSchema,
  readOnly: z.boolean()
});

export type AgentRole = z.infer<typeof AgentRoleSchema>;
export type AgentRuntime = z.infer<typeof AgentRuntimeSchema>;
export type AgentTaskSpec = z.infer<typeof AgentTaskSpecSchema>;
```

- [ ] **Step 3: Create Agent result schema**

Create `ai-orchestrator/src/domain/agent-result.ts`:

```ts
import { z } from "zod";
import { AgentRoleSchema } from "./agent-task-spec.js";

export const AgentStatusSchema = z.enum(["success", "failed", "needs-human"]);
export const RiskLevelSchema = z.enum(["low", "medium", "high"]);

export const AgentResultSchema = z.object({
  agentName: AgentRoleSchema,
  status: AgentStatusSchema,
  summary: z.string().min(1),
  rootCause: z.string().optional(),
  changedFiles: z.array(z.string()),
  patchFile: z.string().nullable(),
  verificationCommand: z.string().nullable(),
  riskLevel: RiskLevelSchema,
  needsHumanReview: z.boolean()
});

export type AgentResult = z.infer<typeof AgentResultSchema>;
export type AgentStatus = z.infer<typeof AgentStatusSchema>;
export type RiskLevel = z.infer<typeof RiskLevelSchema>;
```

- [ ] **Step 4: Create orchestration run schema**

Create `ai-orchestrator/src/domain/orchestration-run.ts`:

```ts
import { z } from "zod";
import { AgentResultSchema } from "./agent-result.js";
import { AgentTaskSpecSchema } from "./agent-task-spec.js";
import { CiFailureEventSchema } from "./ci-event.js";

export const FailureTypeSchema = z.enum([
  "MAVEN_COMPILE_FAILURE",
  "UNIT_TEST_FAILURE",
  "DEPENDENCY_FAILURE",
  "ENVIRONMENT_FAILURE",
  "UNKNOWN_FAILURE"
]);

export const FailureAnalysisSchema = z.object({
  failureType: FailureTypeSchema,
  module: z.string().nullable(),
  failedTests: z.array(z.string()),
  summary: z.string(),
  confidence: z.number().min(0).max(1)
});

export const OrchestrationRunSchema = z.object({
  runId: z.string().min(1),
  event: CiFailureEventSchema,
  analysis: FailureAnalysisSchema,
  tasks: z.array(AgentTaskSpecSchema),
  results: z.array(AgentResultSchema),
  status: z.enum(["planned", "running", "verified", "needs-human", "failed"])
});

export type FailureType = z.infer<typeof FailureTypeSchema>;
export type FailureAnalysis = z.infer<typeof FailureAnalysisSchema>;
export type OrchestrationRun = z.infer<typeof OrchestrationRunSchema>;
```

- [ ] **Step 5: Run typecheck**

Run:

```bash
cd ai-orchestrator
npm run build
```

Expected:

```text
No TypeScript errors
```

- [ ] **Step 6: Commit schemas**

```bash
git add ai-orchestrator/src/domain
git commit -m "feat: define orchestrator domain schemas"
```

---

### Task 3: Add Artifact Fixtures and Surefire Parser

**Files:**
- Create: `ai-orchestrator/examples/github-actions-failure-event.json`
- Create: `ai-orchestrator/examples/surefire-failure-report.xml`
- Create: `ai-orchestrator/src/artifacts/surefire-parser.ts`
- Create: `ai-orchestrator/tests/surefire-parser.test.ts`

- [ ] **Step 1: Create example CI event**

Create `ai-orchestrator/examples/github-actions-failure-event.json`:

```json
{
  "eventId": "ci-20260529-0001",
  "provider": "github-actions",
  "repository": "EnterpriseKnowledgeWorkspace",
  "branch": "feature/agent-cicd",
  "commitSha": "abc123",
  "pullRequest": 42,
  "workflow": "maven-ci",
  "job": "test",
  "status": "failure",
  "artifacts": {
    "ciLog": "examples/ci.log",
    "surefireReports": "examples/surefire-failure-report.xml",
    "gitDiff": "examples/git.diff",
    "moduleInfo": "examples/module-info.json"
  },
  "triggeredAt": "2026-05-29T10:00:00+08:00"
}
```

- [ ] **Step 2: Create Surefire XML fixture**

Create `ai-orchestrator/examples/surefire-failure-report.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<testsuite name="com.zjl.knowledge.service.KbDocumentServiceImplTest" tests="2" failures="1" errors="0" skipped="0">
  <testcase classname="com.zjl.knowledge.service.KbDocumentServiceImplTest" name="startChunkShouldMovePendingDocumentToRunning" time="0.02">
    <failure message="expected: &lt;RUNNING&gt; but was: &lt;FAILED&gt;" type="org.opentest4j.AssertionFailedError">
      org.opentest4j.AssertionFailedError: expected: &lt;RUNNING&gt; but was: &lt;FAILED&gt;
    </failure>
  </testcase>
  <testcase classname="com.zjl.knowledge.service.KbDocumentServiceImplTest" name="uploadShouldCreatePendingDocument" time="0.01" />
</testsuite>
```

- [ ] **Step 3: Write failing parser test**

Create `ai-orchestrator/tests/surefire-parser.test.ts`:

```ts
import { readFile } from "node:fs/promises";
import { describe, expect, it } from "vitest";
import { parseSurefireReport } from "../src/artifacts/surefire-parser.js";

describe("parseSurefireReport", () => {
  it("extracts failed test cases from a Surefire XML report", async () => {
    const xml = await readFile("examples/surefire-failure-report.xml", "utf8");

    const result = parseSurefireReport(xml);

    expect(result.totalTests).toBe(2);
    expect(result.failures).toHaveLength(1);
    expect(result.failures[0]).toEqual({
      className: "com.zjl.knowledge.service.KbDocumentServiceImplTest",
      testName: "startChunkShouldMovePendingDocumentToRunning",
      message: "expected: <RUNNING> but was: <FAILED>",
      type: "org.opentest4j.AssertionFailedError"
    });
  });
});
```

- [ ] **Step 4: Run parser test and verify it fails**

Run:

```bash
cd ai-orchestrator
npm test -- surefire-parser.test.ts
```

Expected:

```text
Cannot find module '../src/artifacts/surefire-parser.js'
```

- [ ] **Step 5: Implement Surefire parser**

Create `ai-orchestrator/src/artifacts/surefire-parser.ts`:

```ts
import { XMLParser } from "fast-xml-parser";

export interface SurefireFailure {
  className: string;
  testName: string;
  message: string;
  type: string;
}

export interface SurefireReport {
  totalTests: number;
  failures: SurefireFailure[];
}

const parser = new XMLParser({
  ignoreAttributes: false,
  attributeNamePrefix: "",
  textNodeName: "text"
});

export function parseSurefireReport(xml: string): SurefireReport {
  const parsed = parser.parse(xml) as {
    testsuite?: {
      tests?: string;
      testcase?: unknown;
    };
  };

  const suite = parsed.testsuite;
  if (!suite) {
    return { totalTests: 0, failures: [] };
  }

  const testCases = Array.isArray(suite.testcase)
    ? suite.testcase
    : suite.testcase
      ? [suite.testcase]
      : [];

  const failures = testCases.flatMap((testCase) => {
    const item = testCase as {
      classname?: string;
      name?: string;
      failure?: {
        message?: string;
        type?: string;
      };
    };

    if (!item.failure) {
      return [];
    }

    return [
      {
        className: item.classname ?? "",
        testName: item.name ?? "",
        message: item.failure.message ?? "",
        type: item.failure.type ?? ""
      }
    ];
  });

  return {
    totalTests: Number(suite.tests ?? testCases.length),
    failures
  };
}
```

- [ ] **Step 6: Run parser test and build**

Run:

```bash
cd ai-orchestrator
npm test -- surefire-parser.test.ts
npm run build
```

Expected:

```text
1 test passed
No TypeScript errors
```

- [ ] **Step 7: Commit parser**

```bash
git add ai-orchestrator/examples ai-orchestrator/src/artifacts/surefire-parser.ts ai-orchestrator/tests/surefire-parser.test.ts
git commit -m "feat: parse surefire failure reports"
```

---

### Task 4: Implement CI Log Analyzer

**Files:**
- Create: `ai-orchestrator/src/analyzer/ci-log-analyzer.ts`
- Create: `ai-orchestrator/tests/ci-log-analyzer.test.ts`

- [ ] **Step 1: Write failing analyzer tests**

Create `ai-orchestrator/tests/ci-log-analyzer.test.ts`:

```ts
import { describe, expect, it } from "vitest";
import { analyzeCiLog } from "../src/analyzer/ci-log-analyzer.js";

describe("analyzeCiLog", () => {
  it("classifies Maven compilation failures and extracts the module", () => {
    const log = [
      "[INFO] Building enterprise-knowledge-ai-service 1.0-SNAPSHOT",
      "[ERROR] COMPILATION ERROR :",
      "[ERROR] /workspace/enterprise-knowledge-ai-service/src/main/java/com/zjl/Foo.java:[12,8] cannot find symbol"
    ].join("\n");

    const analysis = analyzeCiLog(log, []);

    expect(analysis).toEqual({
      failureType: "MAVEN_COMPILE_FAILURE",
      module: "enterprise-knowledge-ai-service",
      failedTests: [],
      summary: "Maven compilation failed in enterprise-knowledge-ai-service.",
      confidence: 0.9
    });
  });

  it("classifies unit test failures using Surefire failures", () => {
    const log = "[ERROR] There are test failures.\n[ERROR] Please refer to target/surefire-reports";

    const analysis = analyzeCiLog(log, ["KbDocumentServiceImplTest.startChunkShouldMovePendingDocumentToRunning"]);

    expect(analysis.failureType).toBe("UNIT_TEST_FAILURE");
    expect(analysis.module).toBeNull();
    expect(analysis.failedTests).toEqual(["KbDocumentServiceImplTest.startChunkShouldMovePendingDocumentToRunning"]);
    expect(analysis.confidence).toBe(0.85);
  });
});
```

- [ ] **Step 2: Run analyzer test and verify it fails**

Run:

```bash
cd ai-orchestrator
npm test -- ci-log-analyzer.test.ts
```

Expected:

```text
Cannot find module '../src/analyzer/ci-log-analyzer.js'
```

- [ ] **Step 3: Implement analyzer**

Create `ai-orchestrator/src/analyzer/ci-log-analyzer.ts`:

```ts
import type { FailureAnalysis } from "../domain/orchestration-run.js";

const modulePattern = /\[INFO\] Building ([a-zA-Z0-9_.-]+) /;

export function analyzeCiLog(log: string, failedTests: string[]): FailureAnalysis {
  const moduleMatch = log.match(modulePattern);
  const moduleName = moduleMatch?.[1] ?? null;

  if (log.includes("COMPILATION ERROR")) {
    return {
      failureType: "MAVEN_COMPILE_FAILURE",
      module: moduleName,
      failedTests: [],
      summary: moduleName ? `Maven compilation failed in ${moduleName}.` : "Maven compilation failed.",
      confidence: 0.9
    };
  }

  if (log.includes("There are test failures") || failedTests.length > 0) {
    return {
      failureType: "UNIT_TEST_FAILURE",
      module: moduleName,
      failedTests,
      summary: failedTests.length > 0
        ? `Unit tests failed: ${failedTests.join(", ")}.`
        : "Maven unit tests failed.",
      confidence: 0.85
    };
  }

  if (log.includes("Could not resolve dependencies") || log.includes("DependencyResolutionException")) {
    return {
      failureType: "DEPENDENCY_FAILURE",
      module: moduleName,
      failedTests: [],
      summary: "Maven dependency resolution failed.",
      confidence: 0.8
    };
  }

  return {
    failureType: "UNKNOWN_FAILURE",
    module: moduleName,
    failedTests,
    summary: "CI failure type could not be classified by MVP rules.",
    confidence: 0.3
  };
}
```

- [ ] **Step 4: Run analyzer tests and build**

Run:

```bash
cd ai-orchestrator
npm test -- ci-log-analyzer.test.ts
npm run build
```

Expected:

```text
2 tests passed
No TypeScript errors
```

- [ ] **Step 5: Commit analyzer**

```bash
git add ai-orchestrator/src/analyzer/ci-log-analyzer.ts ai-orchestrator/tests/ci-log-analyzer.test.ts
git commit -m "feat: classify maven ci failures"
```

---

### Task 5: Implement Task Planner

**Files:**
- Create: `ai-orchestrator/src/planner/task-planner.ts`
- Create: `ai-orchestrator/tests/task-planner.test.ts`

- [ ] **Step 1: Write failing planner tests**

Create `ai-orchestrator/tests/task-planner.test.ts`:

```ts
import { describe, expect, it } from "vitest";
import type { CiFailureEvent } from "../src/domain/ci-event.js";
import type { FailureAnalysis } from "../src/domain/orchestration-run.js";
import { planTasks } from "../src/planner/task-planner.js";

const event: CiFailureEvent = {
  eventId: "ci-1",
  provider: "github-actions",
  repository: "EnterpriseKnowledgeWorkspace",
  branch: "feature/example",
  commitSha: "abc123",
  pullRequest: 42,
  workflow: "maven-ci",
  job: "test",
  status: "failure",
  artifacts: {
    ciLog: "artifacts/ci.log",
    surefireReports: "artifacts/surefire-reports"
  },
  triggeredAt: "2026-05-29T10:00:00+08:00"
};

describe("planTasks", () => {
  it("plans build repair tasks for Maven compile failures", () => {
    const analysis: FailureAnalysis = {
      failureType: "MAVEN_COMPILE_FAILURE",
      module: "enterprise-knowledge-ai-service",
      failedTests: [],
      summary: "Maven compilation failed.",
      confidence: 0.9
    };

    const tasks = planTasks(event, analysis, {
      runtime: "mock",
      workspaceRoot: "/tmp/orchestrator"
    });

    expect(tasks.map((task) => task.role)).toEqual([
      "context-agent",
      "build-fix-agent",
      "review-agent",
      "pr-writer-agent"
    ]);
    expect(tasks[0].readOnly).toBe(true);
    expect(tasks[1].readOnly).toBe(false);
  });

  it("plans test analysis and repair tasks for unit test failures", () => {
    const analysis: FailureAnalysis = {
      failureType: "UNIT_TEST_FAILURE",
      module: "enterprise-knowledge-ai-service",
      failedTests: ["KbDocumentServiceImplTest.startChunkShouldMovePendingDocumentToRunning"],
      summary: "Unit tests failed.",
      confidence: 0.85
    };

    const tasks = planTasks(event, analysis, {
      runtime: "mock",
      workspaceRoot: "/tmp/orchestrator"
    });

    expect(tasks.map((task) => task.role)).toEqual([
      "context-agent",
      "test-analysis-agent",
      "code-fix-agent",
      "test-writer-agent",
      "review-agent",
      "pr-writer-agent"
    ]);
  });
});
```

- [ ] **Step 2: Run planner test and verify it fails**

Run:

```bash
cd ai-orchestrator
npm test -- task-planner.test.ts
```

Expected:

```text
Cannot find module '../src/planner/task-planner.js'
```

- [ ] **Step 3: Implement planner**

Create `ai-orchestrator/src/planner/task-planner.ts`:

```ts
import type { AgentRole, AgentRuntime, AgentTaskSpec } from "../domain/agent-task-spec.js";
import type { CiFailureEvent } from "../domain/ci-event.js";
import type { FailureAnalysis } from "../domain/orchestration-run.js";

export interface PlannerOptions {
  runtime: AgentRuntime;
  workspaceRoot: string;
}

const baseRepoContext = [
  "AGENTS.md",
  "docs/AGENTS.md",
  "docs/api.md",
  "docs/database.md",
  "docs/deployment.md",
  "docs/step3-summary.md",
  "pom.xml"
];

const baseConstraints = [
  "Do not modify protected branches",
  "Do not read production secrets",
  "Follow repository AGENTS.md and docs/AGENTS.md",
  "Use Javadoc block comments only for Java comments",
  "Return structured AgentResult JSON"
];

export function planTasks(
  event: CiFailureEvent,
  analysis: FailureAnalysis,
  options: PlannerOptions
): AgentTaskSpec[] {
  const roles = rolesForFailure(analysis);

  return roles.map((role, index) => ({
    taskId: `${event.eventId}-${index + 1}-${role}`,
    role,
    runtime: options.runtime,
    workspace: `${options.workspaceRoot}/worktrees/${role}`,
    inputArtifacts: event.artifacts,
    repoContext: analysis.module
      ? [...baseRepoContext, `${analysis.module}/pom.xml`]
      : baseRepoContext,
    constraints: baseConstraints,
    expectedOutput: expectedOutputForRole(role),
    readOnly: role === "context-agent" || role === "test-analysis-agent" || role === "review-agent"
  }));
}

function rolesForFailure(analysis: FailureAnalysis): AgentRole[] {
  if (analysis.failureType === "MAVEN_COMPILE_FAILURE" || analysis.failureType === "DEPENDENCY_FAILURE") {
    return ["context-agent", "build-fix-agent", "review-agent", "pr-writer-agent"];
  }

  if (analysis.failureType === "UNIT_TEST_FAILURE") {
    return [
      "context-agent",
      "test-analysis-agent",
      "code-fix-agent",
      "test-writer-agent",
      "review-agent",
      "pr-writer-agent"
    ];
  }

  return ["context-agent", "review-agent", "pr-writer-agent"];
}

function expectedOutputForRole(role: AgentRole): AgentTaskSpec["expectedOutput"] {
  if (role === "build-fix-agent" || role === "code-fix-agent" || role === "test-writer-agent") {
    return { type: "patch", format: "json" };
  }

  if (role === "review-agent") {
    return { type: "review", format: "json" };
  }

  if (role === "pr-writer-agent") {
    return { type: "pr-summary", format: "markdown" };
  }

  return { type: "analysis-report", format: "json" };
}
```

- [ ] **Step 4: Run planner tests and build**

Run:

```bash
cd ai-orchestrator
npm test -- task-planner.test.ts
npm run build
```

Expected:

```text
2 tests passed
No TypeScript errors
```

- [ ] **Step 5: Commit planner**

```bash
git add ai-orchestrator/src/planner/task-planner.ts ai-orchestrator/tests/task-planner.test.ts
git commit -m "feat: plan multi agent repair tasks"
```

---

### Task 6: Implement Runtime Adapter Interface and Mock Adapter

**Files:**
- Create: `ai-orchestrator/src/adapters/agent-runtime-adapter.ts`
- Create: `ai-orchestrator/src/adapters/mock-agent-adapter.ts`
- Create: `ai-orchestrator/src/adapters/codex-cli-adapter.ts`
- Create: `ai-orchestrator/src/utils/command-runner.ts`

- [ ] **Step 1: Create command runner**

Create `ai-orchestrator/src/utils/command-runner.ts`:

```ts
import { execa } from "execa";

export interface CommandResult {
  exitCode: number;
  stdout: string;
  stderr: string;
}

export async function runCommand(
  command: string,
  args: string[],
  cwd: string
): Promise<CommandResult> {
  try {
    const result = await execa(command, args, {
      cwd,
      reject: false,
      all: false
    });

    return {
      exitCode: result.exitCode ?? 0,
      stdout: result.stdout,
      stderr: result.stderr
    };
  } catch (error) {
    const message = error instanceof Error ? error.message : String(error);
    return {
      exitCode: 1,
      stdout: "",
      stderr: message
    };
  }
}
```

- [ ] **Step 2: Create adapter interface**

Create `ai-orchestrator/src/adapters/agent-runtime-adapter.ts`:

```ts
import type { AgentResult } from "../domain/agent-result.js";
import type { AgentTaskSpec } from "../domain/agent-task-spec.js";

export interface AgentRuntimeAdapter {
  readonly runtime: string;
  run(task: AgentTaskSpec): Promise<AgentResult>;
}
```

- [ ] **Step 3: Create mock adapter**

Create `ai-orchestrator/src/adapters/mock-agent-adapter.ts`:

```ts
import type { AgentResult } from "../domain/agent-result.js";
import type { AgentTaskSpec } from "../domain/agent-task-spec.js";
import type { AgentRuntimeAdapter } from "./agent-runtime-adapter.js";

export class MockAgentAdapter implements AgentRuntimeAdapter {
  readonly runtime = "mock";

  async run(task: AgentTaskSpec): Promise<AgentResult> {
    return {
      agentName: task.role,
      status: "success",
      summary: `Mock ${task.role} completed for ${task.taskId}.`,
      rootCause: task.role.includes("analysis") ? "Mock analysis root cause." : undefined,
      changedFiles: task.readOnly ? [] : ["mock-change.txt"],
      patchFile: task.readOnly ? null : `${task.workspace}/mock.patch`,
      verificationCommand: task.readOnly ? null : "mvn test",
      riskLevel: task.readOnly ? "low" : "medium",
      needsHumanReview: true
    };
  }
}
```

- [ ] **Step 4: Create Codex CLI adapter**

Create `ai-orchestrator/src/adapters/codex-cli-adapter.ts`:

```ts
import { AgentResultSchema, type AgentResult } from "../domain/agent-result.js";
import type { AgentTaskSpec } from "../domain/agent-task-spec.js";
import { runCommand } from "../utils/command-runner.js";
import type { AgentRuntimeAdapter } from "./agent-runtime-adapter.js";

export interface CodexCliAdapterOptions {
  command: string;
}

export class CodexCliAdapter implements AgentRuntimeAdapter {
  readonly runtime = "codex";

  constructor(private readonly options: CodexCliAdapterOptions) {}

  async run(task: AgentTaskSpec): Promise<AgentResult> {
    const prompt = [
      "You are an agent worker in an AI CI/CD repair pipeline.",
      "Read the following JSON task and return only AgentResult JSON.",
      JSON.stringify(task, null, 2)
    ].join("\n\n");

    const result = await runCommand(this.options.command, ["run", "--json", prompt], task.workspace);
    if (result.exitCode !== 0) {
      return {
        agentName: task.role,
        status: "failed",
        summary: `Codex CLI failed: ${result.stderr}`,
        changedFiles: [],
        patchFile: null,
        verificationCommand: null,
        riskLevel: "high",
        needsHumanReview: true
      };
    }

    const parsed = AgentResultSchema.safeParse(JSON.parse(result.stdout));
    if (!parsed.success) {
      return {
        agentName: task.role,
        status: "needs-human",
        summary: `Codex CLI returned invalid AgentResult: ${parsed.error.message}`,
        changedFiles: [],
        patchFile: null,
        verificationCommand: null,
        riskLevel: "high",
        needsHumanReview: true
      };
    }

    return parsed.data;
  }
}
```

- [ ] **Step 5: Run build**

Run:

```bash
cd ai-orchestrator
npm run build
```

Expected:

```text
No TypeScript errors
```

- [ ] **Step 6: Commit adapters**

```bash
git add ai-orchestrator/src/adapters ai-orchestrator/src/utils/command-runner.ts
git commit -m "feat: add agent runtime adapters"
```

---

### Task 7: Implement Orchestrator Core

**Files:**
- Create: `ai-orchestrator/src/artifacts/artifact-loader.ts`
- Create: `ai-orchestrator/src/orchestrator.ts`
- Create: `ai-orchestrator/tests/orchestrator.test.ts`

- [ ] **Step 1: Write orchestrator test**

Create `ai-orchestrator/tests/orchestrator.test.ts`:

```ts
import { describe, expect, it } from "vitest";
import { MockAgentAdapter } from "../src/adapters/mock-agent-adapter.js";
import type { CiFailureEvent } from "../src/domain/ci-event.js";
import { runOrchestration } from "../src/orchestrator.js";

describe("runOrchestration", () => {
  it("creates a planned run and executes mock agents", async () => {
    const event: CiFailureEvent = {
      eventId: "ci-1",
      provider: "github-actions",
      repository: "EnterpriseKnowledgeWorkspace",
      branch: "feature/example",
      commitSha: "abc123",
      pullRequest: 42,
      workflow: "maven-ci",
      job: "test",
      status: "failure",
      artifacts: {
        ciLog: "inline"
      },
      triggeredAt: "2026-05-29T10:00:00+08:00"
    };

    const run = await runOrchestration({
      event,
      ciLog: "[ERROR] There are test failures.",
      failedTests: ["KbDocumentServiceImplTest.startChunkShouldMovePendingDocumentToRunning"],
      runtime: "mock",
      workspaceRoot: "/tmp/orchestrator-test",
      adapters: {
        mock: new MockAgentAdapter()
      }
    });

    expect(run.status).toBe("running");
    expect(run.analysis.failureType).toBe("UNIT_TEST_FAILURE");
    expect(run.tasks).toHaveLength(6);
    expect(run.results).toHaveLength(6);
  });
});
```

- [ ] **Step 2: Run orchestrator test and verify it fails**

Run:

```bash
cd ai-orchestrator
npm test -- orchestrator.test.ts
```

Expected:

```text
Cannot find module '../src/orchestrator.js'
```

- [ ] **Step 3: Create artifact loader**

Create `ai-orchestrator/src/artifacts/artifact-loader.ts`:

```ts
import { readFile } from "node:fs/promises";
import { parseSurefireReport } from "./surefire-parser.js";

export async function loadTextArtifact(path: string): Promise<string> {
  return readFile(path, "utf8");
}

export async function loadFailedTestsFromSurefire(path: string): Promise<string[]> {
  const xml = await readFile(path, "utf8");
  const report = parseSurefireReport(xml);
  return report.failures.map((failure) => `${failure.className}.${failure.testName}`);
}
```

- [ ] **Step 4: Implement orchestrator core**

Create `ai-orchestrator/src/orchestrator.ts`:

```ts
import type { AgentRuntimeAdapter } from "./adapters/agent-runtime-adapter.js";
import type { AgentRuntime } from "./domain/agent-task-spec.js";
import type { CiFailureEvent } from "./domain/ci-event.js";
import type { OrchestrationRun } from "./domain/orchestration-run.js";
import { analyzeCiLog } from "./analyzer/ci-log-analyzer.js";
import { planTasks } from "./planner/task-planner.js";

export interface RunOrchestrationInput {
  event: CiFailureEvent;
  ciLog: string;
  failedTests: string[];
  runtime: AgentRuntime;
  workspaceRoot: string;
  adapters: Partial<Record<AgentRuntime, AgentRuntimeAdapter>>;
}

export async function runOrchestration(input: RunOrchestrationInput): Promise<OrchestrationRun> {
  const analysis = analyzeCiLog(input.ciLog, input.failedTests);
  const tasks = planTasks(input.event, analysis, {
    runtime: input.runtime,
    workspaceRoot: input.workspaceRoot
  });

  const adapter = input.adapters[input.runtime];
  if (!adapter) {
    throw new Error(`No adapter configured for runtime ${input.runtime}`);
  }

  const results = [];
  for (const task of tasks) {
    results.push(await adapter.run(task));
  }

  return {
    runId: `${input.event.eventId}-${Date.now()}`,
    event: input.event,
    analysis,
    tasks,
    results,
    status: results.some((result) => result.status === "failed") ? "failed" : "running"
  };
}
```

- [ ] **Step 5: Run orchestrator tests and build**

Run:

```bash
cd ai-orchestrator
npm test -- orchestrator.test.ts
npm run build
```

Expected:

```text
1 test passed
No TypeScript errors
```

- [ ] **Step 6: Commit orchestrator core**

```bash
git add ai-orchestrator/src/artifacts/artifact-loader.ts ai-orchestrator/src/orchestrator.ts ai-orchestrator/tests/orchestrator.test.ts
git commit -m "feat: execute orchestrator task pipeline"
```

---

### Task 8: Add Worktree, Patch, Verification, Review, and PR MVP Components

**Files:**
- Create: `ai-orchestrator/src/worktree/worktree-manager.ts`
- Create: `ai-orchestrator/src/patch/patch-merger.ts`
- Create: `ai-orchestrator/src/verification/verification-runner.ts`
- Create: `ai-orchestrator/src/review/review-gate.ts`
- Create: `ai-orchestrator/src/pr/pr-creator.ts`

- [ ] **Step 1: Create worktree manager**

Create `ai-orchestrator/src/worktree/worktree-manager.ts`:

```ts
import { mkdir } from "node:fs/promises";
import { simpleGit } from "simple-git";
import type { AgentTaskSpec } from "../domain/agent-task-spec.js";

export async function prepareWorktree(repoPath: string, baseBranch: string, task: AgentTaskSpec): Promise<string> {
  await mkdir(task.workspace, { recursive: true });
  const git = simpleGit(repoPath);
  const branchName = `ai/${task.taskId}`;
  await git.raw(["worktree", "add", "-B", branchName, task.workspace, baseBranch]);
  return task.workspace;
}
```

- [ ] **Step 2: Create patch merger**

Create `ai-orchestrator/src/patch/patch-merger.ts`:

```ts
import type { AgentResult } from "../domain/agent-result.js";
import { runCommand } from "../utils/command-runner.js";

export async function applyTrustedPatches(repoPath: string, results: AgentResult[]): Promise<string[]> {
  const applied: string[] = [];

  for (const result of results) {
    if (!result.patchFile || result.status !== "success") {
      continue;
    }

    const command = await runCommand("git", ["apply", result.patchFile], repoPath);
    if (command.exitCode !== 0) {
      throw new Error(`Failed to apply ${result.patchFile}: ${command.stderr}`);
    }
    applied.push(result.patchFile);
  }

  return applied;
}
```

- [ ] **Step 3: Create verification runner**

Create `ai-orchestrator/src/verification/verification-runner.ts`:

```ts
import type { FailureAnalysis } from "../domain/orchestration-run.js";
import { runCommand, type CommandResult } from "../utils/command-runner.js";

export function selectVerificationCommand(analysis: FailureAnalysis): string[] {
  if (analysis.module) {
    return ["mvn", "test", "-pl", analysis.module];
  }
  return ["mvn", "test"];
}

export async function runVerification(repoPath: string, analysis: FailureAnalysis): Promise<CommandResult> {
  const [command, ...args] = selectVerificationCommand(analysis);
  return runCommand(command, args, repoPath);
}
```

- [ ] **Step 4: Create review gate**

Create `ai-orchestrator/src/review/review-gate.ts`:

```ts
import type { AgentResult } from "../domain/agent-result.js";

export interface ReviewGateResult {
  passed: boolean;
  reasons: string[];
}

const forbiddenPaths = [
  "application-secrets.yml",
  ".env",
  "id_rsa",
  "credentials"
];

export function evaluateReviewGate(results: AgentResult[]): ReviewGateResult {
  const reasons: string[] = [];

  for (const result of results) {
    for (const file of result.changedFiles) {
      if (forbiddenPaths.some((path) => file.includes(path))) {
        reasons.push(`Forbidden file changed: ${file}`);
      }
    }

    if (result.riskLevel === "high") {
      reasons.push(`High risk result from ${result.agentName}: ${result.summary}`);
    }
  }

  return {
    passed: reasons.length === 0,
    reasons
  };
}
```

- [ ] **Step 5: Create dry-run PR creator**

Create `ai-orchestrator/src/pr/pr-creator.ts`:

```ts
import { mkdir, writeFile } from "node:fs/promises";
import { join } from "node:path";
import type { OrchestrationRun } from "../domain/orchestration-run.js";

export async function writePrSummaryArtifact(outputDir: string, run: OrchestrationRun): Promise<string> {
  await mkdir(outputDir, { recursive: true });
  const path = join(outputDir, `${run.runId}-pr-summary.md`);
  const body = [
    `# AI Repair PR Summary`,
    ``,
    `Run: ${run.runId}`,
    `Failure Type: ${run.analysis.failureType}`,
    `Module: ${run.analysis.module ?? "unknown"}`,
    ``,
    `## Summary`,
    run.analysis.summary,
    ``,
    `## Agent Results`,
    ...run.results.map((result) => `- ${result.agentName}: ${result.status} - ${result.summary}`)
  ].join("\n");

  await writeFile(path, body, "utf8");
  return path;
}
```

- [ ] **Step 6: Run build**

Run:

```bash
cd ai-orchestrator
npm run build
```

Expected:

```text
No TypeScript errors
```

- [ ] **Step 7: Commit MVP components**

```bash
git add ai-orchestrator/src/worktree ai-orchestrator/src/patch ai-orchestrator/src/verification ai-orchestrator/src/review ai-orchestrator/src/pr
git commit -m "feat: add repair pipeline support components"
```

---

### Task 9: Add HTTP Server and CLI Entry Point

**Files:**
- Create: `ai-orchestrator/src/config.ts`
- Create: `ai-orchestrator/src/server.ts`
- Create: `ai-orchestrator/src/index.ts`

- [ ] **Step 1: Create config loader**

Create `ai-orchestrator/src/config.ts`:

```ts
import { z } from "zod";

const ConfigSchema = z.object({
  port: z.number().int().positive(),
  workspaceRoot: z.string().min(1),
  defaultRuntime: z.enum(["mock", "codex", "claude", "gemini", "custom"]),
  maxRepairAttempts: z.number().int().positive(),
  codexCliCommand: z.string().min(1)
});

export type OrchestratorConfig = z.infer<typeof ConfigSchema>;

export function loadConfig(env: NodeJS.ProcessEnv = process.env): OrchestratorConfig {
  return ConfigSchema.parse({
    port: Number(env.ORCHESTRATOR_PORT ?? 8095),
    workspaceRoot: env.ORCHESTRATOR_WORKSPACE_ROOT ?? "/tmp/enterprise-ai-orchestrator",
    defaultRuntime: env.ORCHESTRATOR_DEFAULT_RUNTIME ?? "mock",
    maxRepairAttempts: Number(env.ORCHESTRATOR_MAX_REPAIR_ATTEMPTS ?? 2),
    codexCliCommand: env.CODEX_CLI_COMMAND ?? "codex"
  });
}
```

- [ ] **Step 2: Create Fastify server**

Create `ai-orchestrator/src/server.ts`:

```ts
import Fastify from "fastify";
import sensible from "@fastify/sensible";
import { CodexCliAdapter } from "./adapters/codex-cli-adapter.js";
import { MockAgentAdapter } from "./adapters/mock-agent-adapter.js";
import { loadConfig } from "./config.js";
import { CiFailureEventSchema } from "./domain/ci-event.js";
import { runOrchestration } from "./orchestrator.js";

export async function buildServer() {
  const config = loadConfig();
  const server = Fastify({ logger: true });
  await server.register(sensible);

  server.get("/health", async () => ({ status: "ok" }));

  server.post("/webhooks/ci-failure", async (request, reply) => {
    const parsed = CiFailureEventSchema.safeParse(request.body);
    if (!parsed.success) {
      return reply.badRequest(parsed.error.message);
    }

    const run = await runOrchestration({
      event: parsed.data,
      ciLog: "MVP webhook received. Artifact download is handled by CI artifact preparation.",
      failedTests: [],
      runtime: config.defaultRuntime,
      workspaceRoot: config.workspaceRoot,
      adapters: {
        mock: new MockAgentAdapter(),
        codex: new CodexCliAdapter({ command: config.codexCliCommand })
      }
    });

    return reply.send(run);
  });

  return server;
}
```

- [ ] **Step 3: Create entry point**

Create `ai-orchestrator/src/index.ts`:

```ts
import { loadConfig } from "./config.js";
import { buildServer } from "./server.js";

const config = loadConfig();
const server = await buildServer();

await server.listen({
  port: config.port,
  host: "0.0.0.0"
});
```

- [ ] **Step 4: Run build and server smoke test**

Run:

```bash
cd ai-orchestrator
npm run build
npm run dev
```

Expected:

```text
Server listening at http://0.0.0.0:8095
```

Stop the dev server after confirming startup.

- [ ] **Step 5: Commit server**

```bash
git add ai-orchestrator/src/config.ts ai-orchestrator/src/server.ts ai-orchestrator/src/index.ts
git commit -m "feat: expose ci failure webhook"
```

---

### Task 10: Add GitHub Actions Maven CI Workflow

**Files:**
- Create: `.github/workflows/maven-ci.yml`

- [ ] **Step 1: Create Maven CI workflow**

Create `.github/workflows/maven-ci.yml`:

```yaml
name: Maven CI

on:
  pull_request:
  push:
    branches:
      - main
      - master
      - develop

jobs:
  test:
    runs-on: ubuntu-latest
    permissions:
      contents: read
      actions: read

    steps:
      - name: Checkout
        uses: actions/checkout@v4

      - name: Set up Java
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: "17"
          cache: maven

      - name: Capture module info
        run: |
          mkdir -p artifacts
          mvn -q help:evaluate -Dexpression=project.modules -DforceStdout > artifacts/module-info.txt || true

      - name: Run Maven tests
        run: |
          mkdir -p artifacts
          set -o pipefail
          mvn test 2>&1 | tee artifacts/ci.log

      - name: Capture git diff
        if: always()
        run: |
          mkdir -p artifacts
          git diff -- . > artifacts/git.diff || true

      - name: Upload CI artifacts
        if: always()
        uses: actions/upload-artifact@v4
        with:
          name: ci-failure-context
          path: |
            artifacts/
            **/target/surefire-reports/
```

- [ ] **Step 2: Commit workflow**

```bash
git add .github/workflows/maven-ci.yml
git commit -m "ci: add maven test workflow"
```

---

### Task 11: Update Documentation

**Files:**
- Modify: `docs/ai-agent-cicd-orchestration.md`
- Modify: `docs/deployment.md`
- Modify: `ai-orchestrator/README.md`

- [ ] **Step 1: Add implementation status to design doc**

Append this section to `docs/ai-agent-cicd-orchestration.md`:

```md
## 17. MVP Implementation Status

The first implementation is tracked in `docs/superpowers/plans/2026-05-29-ai-agent-cicd-orchestration.md`.

The MVP creates a standalone `ai-orchestrator/` TypeScript service with:

1. CI failure event schemas.
2. Maven log analysis.
3. Surefire report parsing.
4. Multi-agent task planning.
5. Runtime adapter abstraction.
6. Mock and Codex CLI adapter support.
7. Worktree, patch, verification, review, and PR-summary components.
8. A GitHub Actions Maven CI workflow.
```

- [ ] **Step 2: Add deployment note**

Add this subsection to `docs/deployment.md` after the service list:

```md
## 3.1 AI Orchestrator

`ai-orchestrator` is a standalone TypeScript infrastructure service for CI/CD AI Agent orchestration. It is not a business microservice and must not be exposed as a frontend API.

Deployment rules:

1. Deploy only inside the internal CI/CD network.
2. Grant read access to CI artifacts and limited write access for repair branches.
3. Do not grant production deployment permissions.
4. Do not mount production secret files.
5. Record all Agent task inputs, outputs, changed files, and verification results.
```

- [ ] **Step 3: Add README usage example**

Append this to `ai-orchestrator/README.md`:

```md
## Webhook Example

```bash
curl -X POST http://localhost:8095/webhooks/ci-failure \
  -H 'Content-Type: application/json' \
  --data @examples/github-actions-failure-event.json
```

The MVP returns an orchestration run JSON. Production integration should download CI artifacts before invoking repair agents.
```

- [ ] **Step 4: Commit docs**

```bash
git add docs/ai-agent-cicd-orchestration.md docs/deployment.md ai-orchestrator/README.md
git commit -m "docs: document ai orchestrator mvp"
```

---

### Task 12: Final Verification

**Files:**
- Verify all files created or modified in previous tasks.

- [ ] **Step 1: Run TypeScript tests**

Run:

```bash
cd ai-orchestrator
npm test
```

Expected:

```text
All tests pass
```

- [ ] **Step 2: Run TypeScript build**

Run:

```bash
cd ai-orchestrator
npm run build
```

Expected:

```text
No TypeScript errors
```

- [ ] **Step 3: Run Java Maven smoke test**

Run from repository root:

```bash
mvn test -pl enterprise-knowledge-ai-service
```

Expected:

```text
BUILD SUCCESS
```

If this fails because existing unrelated workspace changes already break tests, capture the failure summary and do not hide it.

- [ ] **Step 4: Inspect git diff**

Run:

```bash
git status --short
git diff --stat
```

Expected:

```text
Only AI Orchestrator, GitHub Actions workflow, and documentation files changed.
```

- [ ] **Step 5: Final commit if any verification-only fixes were needed**

```bash
git add ai-orchestrator .github/workflows/maven-ci.yml docs/ai-agent-cicd-orchestration.md docs/deployment.md
git commit -m "chore: verify ai orchestrator mvp"
```

Skip this commit if there are no additional changes after previous task commits.

---

## Self-Review Checklist

- [ ] The plan implements the design document's MVP scope: GitHub Actions event input, Maven compile/test classification, Surefire parsing, multi-agent task planning, runtime adapter abstraction, worktree/patch/verification/review/PR components, and safety boundaries.
- [ ] The first runtime implementation is mock-safe, with Codex CLI adapter present behind the runtime interface.
- [ ] The plan does not grant production deployment, protected branch merge, or secret-reading capabilities.
- [ ] Every code-producing step includes exact file paths and concrete code.
- [ ] Every test step includes exact commands and expected results.
- [ ] The plan keeps the Orchestrator outside existing Java business microservices.
