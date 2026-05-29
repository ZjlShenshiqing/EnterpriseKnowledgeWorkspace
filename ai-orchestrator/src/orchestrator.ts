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
