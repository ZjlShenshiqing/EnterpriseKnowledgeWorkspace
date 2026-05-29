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
    readOnly: role === "context-agent" || role === "test-analysis-agent" || role === "review-agent",
    dependencies: [],
    inputKeys: [],
    outputKeys: [],
    blackboardContext: {}
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

const blackboardKeys = {
  context: { input: ["artifact:ci-log", "event:ci-failure"], output: ["analysis:context"] },
  buildFix: { input: ["artifact:ci-log", "analysis:failure-type"], output: ["patch:build-fix"] },
  testAnalysis: { input: ["artifact:ci-log", "analysis:failed-tests"], output: ["analysis:test-findings"] },
  codeFix: { input: ["analysis:context", "analysis:test-findings"], output: ["patch:code-fix"] },
  testWriter: { input: ["analysis:context", "analysis:test-findings"], output: ["patch:test-writer"] },
  review: { input: ["analysis:context", "patch:build-fix", "patch:code-fix", "patch:test-writer"], output: ["review:report"] },
  prWriter: { input: ["review:report", "analysis:context"], output: ["pr-summary"] }
};

export function planTasksWithDag(
  event: CiFailureEvent,
  analysis: FailureAnalysis,
  options: PlannerOptions
): AgentTaskSpec[] {
  const tasks: AgentTaskSpec[] = [];
  let index = 0;

  function addTask(role: AgentRole, deps: string[], keys: { input: string[]; output: string[] }): string {
    const taskId = `${event.eventId}-${++index}-${role}`;
    tasks.push({
      taskId,
      role,
      runtime: options.runtime,
      workspace: `${options.workspaceRoot}/worktrees/${role}`,
      inputArtifacts: event.artifacts,
      repoContext: analysis.module
        ? [...baseRepoContext, `${analysis.module}/pom.xml`]
        : baseRepoContext,
      constraints: baseConstraints,
      expectedOutput: expectedOutputForRole(role),
      readOnly: role === "context-agent" || role === "test-analysis-agent" || role === "review-agent",
      dependencies: deps,
      inputKeys: keys.input,
      outputKeys: keys.output,
      blackboardContext: {}
    });
    return taskId;
  }

  if (analysis.failureType === "MAVEN_COMPILE_FAILURE" || analysis.failureType === "DEPENDENCY_FAILURE") {
    const ctxId = addTask("context-agent", [], blackboardKeys.context);
    const fixId = addTask("build-fix-agent", [], blackboardKeys.buildFix);
    const reviewId = addTask("review-agent", [ctxId, fixId], {
      input: ["analysis:context", "patch:build-fix"],
      output: ["review:report"]
    });
    addTask("pr-writer-agent", [reviewId], blackboardKeys.prWriter);
  } else if (analysis.failureType === "UNIT_TEST_FAILURE") {
    const ctxId = addTask("context-agent", [], blackboardKeys.context);
    const testAnalysisId = addTask("test-analysis-agent", [], blackboardKeys.testAnalysis);
    const codeFixId = addTask("code-fix-agent", [ctxId, testAnalysisId], blackboardKeys.codeFix);
    const testWriterId = addTask("test-writer-agent", [ctxId, testAnalysisId], blackboardKeys.testWriter);
    const reviewId = addTask("review-agent", [codeFixId, testWriterId], {
      input: ["analysis:context", "analysis:test-findings", "patch:code-fix", "patch:test-writer"],
      output: ["review:report"]
    });
    addTask("pr-writer-agent", [reviewId], blackboardKeys.prWriter);
  } else {
    const ctxId = addTask("context-agent", [], blackboardKeys.context);
    const reviewId = addTask("review-agent", [ctxId], {
      input: ["analysis:context"],
      output: ["review:report"]
    });
    addTask("pr-writer-agent", [reviewId], blackboardKeys.prWriter);
  }

  return tasks;
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
