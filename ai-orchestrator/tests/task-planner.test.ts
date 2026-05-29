import { describe, expect, it } from "vitest";
import type { CiFailureEvent } from "../src/domain/ci-event.js";
import type { FailureAnalysis } from "../src/domain/orchestration-run.js";
import { planTasks, planTasksWithDag } from "../src/planner/task-planner.js";

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

describe("planTasksWithDag", () => {
  it("compile failure: context-agent and build-fix-agent are independent (no deps)", () => {
    const analysis: FailureAnalysis = {
      failureType: "MAVEN_COMPILE_FAILURE",
      module: "enterprise-knowledge-ai-service",
      failedTests: [],
      summary: "Maven compilation failed.",
      confidence: 0.9
    };

    const tasks = planTasksWithDag(event, analysis, {
      runtime: "mock",
      workspaceRoot: "/tmp/orchestrator"
    });

    const ctx = tasks.find((t) => t.role === "context-agent")!;
    const fix = tasks.find((t) => t.role === "build-fix-agent")!;
    expect(ctx.dependencies).toHaveLength(0);
    expect(fix.dependencies).toHaveLength(0);
    expect(ctx.inputKeys).toEqual(["artifact:ci-log", "event:ci-failure"]);
    expect(ctx.outputKeys).toEqual(["analysis:context"]);
  });

  it("test failure: code-fix-agent and test-writer-agent both depend on test-analysis-agent", () => {
    const analysis: FailureAnalysis = {
      failureType: "UNIT_TEST_FAILURE",
      module: "enterprise-knowledge-ai-service",
      failedTests: ["KbDocumentServiceImplTest.startChunkShouldMovePendingDocumentToRunning"],
      summary: "Unit tests failed.",
      confidence: 0.85
    };

    const tasks = planTasksWithDag(event, analysis, {
      runtime: "mock",
      workspaceRoot: "/tmp/orchestrator"
    });

    const testAnalysis = tasks.find((t) => t.role === "test-analysis-agent")!;
    const codeFix = tasks.find((t) => t.role === "code-fix-agent")!;
    const testWriter = tasks.find((t) => t.role === "test-writer-agent")!;

    expect(testAnalysis.dependencies).toHaveLength(0);
    expect(codeFix.dependencies).toContain(testAnalysis.taskId);
    expect(testWriter.dependencies).toContain(testAnalysis.taskId);
  });

  it("test failure: code-fix-agent and test-writer-agent are independent of each other", () => {
    const analysis: FailureAnalysis = {
      failureType: "UNIT_TEST_FAILURE",
      module: "enterprise-knowledge-ai-service",
      failedTests: ["KbDocumentServiceImplTest.startChunkShouldMovePendingDocumentToRunning"],
      summary: "Unit tests failed.",
      confidence: 0.85
    };

    const tasks = planTasksWithDag(event, analysis, {
      runtime: "mock",
      workspaceRoot: "/tmp/orchestrator"
    });

    const codeFix = tasks.find((t) => t.role === "code-fix-agent")!;
    const testWriter = tasks.find((t) => t.role === "test-writer-agent")!;
    expect(codeFix.dependencies).not.toContain(testWriter.taskId);
    expect(testWriter.dependencies).not.toContain(codeFix.taskId);
  });

  it("all failure types have review-agent before pr-writer-agent", () => {
    for (const failureType of ["MAVEN_COMPILE_FAILURE", "UNIT_TEST_FAILURE"] as const) {
      const analysis: FailureAnalysis = {
        failureType,
        module: null,
        failedTests: [],
        summary: "test",
        confidence: 0.9
      };

      const tasks = planTasksWithDag(event, analysis, {
        runtime: "mock",
        workspaceRoot: "/tmp/orchestrator"
      });

      const review = tasks.find((t) => t.role === "review-agent")!;
      const pr = tasks.find((t) => t.role === "pr-writer-agent")!;
      expect(pr.dependencies).toContain(review.taskId);
    }
  });
});
