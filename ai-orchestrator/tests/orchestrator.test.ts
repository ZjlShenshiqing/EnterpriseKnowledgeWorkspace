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
