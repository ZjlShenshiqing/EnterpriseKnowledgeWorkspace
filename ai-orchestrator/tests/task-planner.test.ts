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
