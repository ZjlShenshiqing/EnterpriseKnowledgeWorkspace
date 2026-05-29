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
