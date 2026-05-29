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
