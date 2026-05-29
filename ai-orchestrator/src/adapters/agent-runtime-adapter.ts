import type { AgentResult } from "../domain/agent-result.js";
import type { AgentTaskSpec } from "../domain/agent-task-spec.js";

export interface AgentRuntimeAdapter {
  readonly runtime: string;
  run(task: AgentTaskSpec): Promise<AgentResult>;
}
