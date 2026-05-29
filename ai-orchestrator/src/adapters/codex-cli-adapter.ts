import { AgentResultSchema, type AgentResult } from "../domain/agent-result.js";
import type { AgentTaskSpec } from "../domain/agent-task-spec.js";
import { runCommand } from "../utils/command-runner.js";
import type { AgentRuntimeAdapter } from "./agent-runtime-adapter.js";

export interface CodexCliAdapterOptions {
  command: string;
}

export class CodexCliAdapter implements AgentRuntimeAdapter {
  readonly runtime = "codex";

  constructor(private readonly options: CodexCliAdapterOptions) {}

  async run(task: AgentTaskSpec): Promise<AgentResult> {
    const prompt = [
      "You are an agent worker in an AI CI/CD repair pipeline.",
      "Read the following JSON task and return only AgentResult JSON.",
      JSON.stringify(task, null, 2)
    ].join("\n\n");

    const result = await runCommand(this.options.command, ["run", "--json", prompt], task.workspace);
    if (result.exitCode !== 0) {
      return {
        agentName: task.role,
        status: "failed",
        summary: `Codex CLI failed: ${result.stderr}`,
        changedFiles: [],
        patchFile: null,
        verificationCommand: null,
        riskLevel: "high",
        needsHumanReview: true
      };
    }

    const parsed = AgentResultSchema.safeParse(JSON.parse(result.stdout));
    if (!parsed.success) {
      return {
        agentName: task.role,
        status: "needs-human",
        summary: `Codex CLI returned invalid AgentResult: ${parsed.error.message}`,
        changedFiles: [],
        patchFile: null,
        verificationCommand: null,
        riskLevel: "high",
        needsHumanReview: true
      };
    }

    return parsed.data;
  }
}
