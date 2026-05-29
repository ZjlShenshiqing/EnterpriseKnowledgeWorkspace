import type { AgentRuntimeAdapter } from "./adapters/agent-runtime-adapter.js";
import type { AgentResult } from "./domain/agent-result.js";
import type { AgentRuntime, AgentTaskSpec } from "./domain/agent-task-spec.js";
import type { CiFailureEvent } from "./domain/ci-event.js";
import type { OrchestrationRun } from "./domain/orchestration-run.js";
import { analyzeCiLog } from "./analyzer/ci-log-analyzer.js";
import { Blackboard } from "./blackboard/blackboard.js";
import { ConflictDetector } from "./conflict/conflict-detector.js";
import { runPipeline } from "./pipeline/pipeline-runner.js";
import { planTasksWithDag } from "./planner/task-planner.js";
import { DagScheduler } from "./scheduler/dag-scheduler.js";

export interface RunOrchestrationInput {
  event: CiFailureEvent;
  ciLog: string;
  failedTests: string[];
  runtime: AgentRuntime;
  workspaceRoot: string;
  adapters: Partial<Record<AgentRuntime, AgentRuntimeAdapter>>;
}

async function runAgentSafe(adapter: AgentRuntimeAdapter, task: AgentTaskSpec): Promise<AgentResult> {
  try {
    return await adapter.run(task);
  } catch (error) {
    const message = error instanceof Error ? error.message : String(error);
    return {
      agentName: task.role,
      status: "failed",
      summary: `Agent crashed: ${message}`,
      changedFiles: [],
      patchFile: null,
      verificationCommand: null,
      riskLevel: "high",
      needsHumanReview: true
    };
  }
}

export async function runOrchestration(input: RunOrchestrationInput): Promise<OrchestrationRun> {
  const analysis = analyzeCiLog(input.ciLog, input.failedTests);

  const tasks = planTasksWithDag(input.event, analysis, {
    runtime: input.runtime,
    workspaceRoot: input.workspaceRoot
  });

  const blackboard = new Blackboard();
  blackboard.write("artifact:ci-log", input.ciLog, "__system__");
  blackboard.write("event:ci-failure", input.event, "__system__");
  blackboard.write("analysis:failure-type", analysis, "__system__");
  blackboard.write("analysis:failed-tests", input.failedTests, "__system__");

  const adapter = input.adapters[input.runtime];
  if (!adapter) {
    throw new Error(`No adapter configured for runtime ${input.runtime}`);
  }

  const scheduler = new DagScheduler();
  const { waves, unscheduled } = scheduler.schedule(tasks);

  for (const taskId of unscheduled) {
    blackboard.write("scheduler:unscheduled", taskId, "__system__");
  }

  const results: AgentResult[] = [];
  for (const wave of waves) {
    for (const task of wave.tasks) {
      task.blackboardContext = blackboard.getRelevantEntries(task.inputKeys);
    }

    const waveResults = await Promise.all(
      wave.tasks.map((task) => runAgentSafe(adapter, task))
    );

    for (let i = 0; i < wave.tasks.length; i++) {
      const task = wave.tasks[i];
      const result = waveResults[i];
      if (result.status === "success") {
        for (const key of task.outputKeys) {
          blackboard.write(key, result, task.taskId, task.role);
        }
      }
    }
    results.push(...waveResults);
  }

  const conflictDetector = new ConflictDetector();
  const conflicts = conflictDetector.detect(results);

  const runId = `${input.event.eventId}-${Date.now()}`;
  const run: OrchestrationRun = {
    runId,
    event: input.event,
    analysis,
    tasks,
    results,
    status: "running",
    conflicts,
    blackboard: blackboard.snapshot()
  };

  if (conflicts.length > 0) {
    run.status = "needs-human";
    return run;
  }

  const pipelineResult = await runPipeline({
    repoPath: input.workspaceRoot,
    analysis,
    results,
    run,
    prOutputDir: `${input.workspaceRoot}/pr-output`
  });

  run.pipelineResult = pipelineResult;
  run.status = pipelineResult.verificationPassed ? "verified" : "failed";
  return run;
}
