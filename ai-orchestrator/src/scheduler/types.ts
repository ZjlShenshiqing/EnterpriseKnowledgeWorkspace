import type { AgentTaskSpec } from "../domain/agent-task-spec.js";

export interface ExecutionWave {
  level: number;
  tasks: AgentTaskSpec[];
}

export interface SchedulingResult {
  waves: ExecutionWave[];
  unscheduled: string[];
}
