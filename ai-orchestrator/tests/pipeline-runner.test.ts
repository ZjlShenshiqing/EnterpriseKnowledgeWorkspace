import { describe, expect, it } from "vitest";
import { runPipeline } from "../src/pipeline/pipeline-runner.js";
import type { AgentResult } from "../src/domain/agent-result.js";
import type { OrchestrationRun } from "../src/domain/orchestration-run.js";

function makeResult(agentName: string, patchFile: string | null): AgentResult {
  return {
    agentName: agentName as AgentResult["agentName"],
    status: "success",
    summary: "ok",
    changedFiles: [],
    patchFile,
    verificationCommand: null,
    riskLevel: "low",
    needsHumanReview: false
  };
}

const baseRun: OrchestrationRun = {
  runId: "test-run-1",
  event: {
    eventId: "ci-1",
    provider: "github-actions",
    repository: "test-repo",
    branch: "main",
    commitSha: "abc123",
    workflow: "maven-ci",
    job: "test",
    status: "failure",
    artifacts: { ciLog: "inline" },
    triggeredAt: "2026-05-29T10:00:00+08:00"
  },
  analysis: {
    failureType: "UNIT_TEST_FAILURE",
    module: null,
    failedTests: [],
    summary: "test",
    confidence: 0.85
  },
  tasks: [],
  results: [],
  status: "running",
  conflicts: []
};

describe("runPipeline", () => {
  it("completes with empty results (no patches to apply)", async () => {
    const result = await runPipeline({
      repoPath: "/tmp/test-repo",
      analysis: baseRun.analysis,
      results: [],
      run: baseRun,
      prOutputDir: "/tmp/test-pr-output"
    });

    expect(result.patchesApplied).toHaveLength(0);
    expect(result.verificationPassed).toBe(true);
    expect(result.reviewGatePassed).toBe(true);
    expect(result.prSummaryPath).toBeDefined();
  });

  it("review gate fails on high-risk result", async () => {
    const results: AgentResult[] = [
      {
        ...makeResult("code-fix-agent", null),
        riskLevel: "high",
        summary: "modified sensitive code"
      }
    ];

    const result = await runPipeline({
      repoPath: "/tmp/test-repo",
      analysis: baseRun.analysis,
      results,
      run: baseRun,
      prOutputDir: "/tmp/test-pr-output"
    });

    expect(result.reviewGatePassed).toBe(false);
    expect(result.reviewGateReasons.length).toBeGreaterThan(0);
  });

  it("review gate fails on forbidden file changes", async () => {
    const results: AgentResult[] = [
      {
        ...makeResult("code-fix-agent", null),
        changedFiles: ["src/.env"]
      }
    ];

    const result = await runPipeline({
      repoPath: "/tmp/test-repo",
      analysis: baseRun.analysis,
      results,
      run: baseRun,
      prOutputDir: "/tmp/test-pr-output"
    });

    expect(result.reviewGatePassed).toBe(false);
    expect(result.reviewGateReasons.some((r) => r.includes(".env"))).toBe(true);
  });
});
