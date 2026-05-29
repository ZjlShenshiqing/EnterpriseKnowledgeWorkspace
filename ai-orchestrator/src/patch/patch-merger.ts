import type { AgentResult } from "../domain/agent-result.js";
import { runCommand } from "../utils/command-runner.js";

export async function applyTrustedPatches(repoPath: string, results: AgentResult[]): Promise<string[]> {
  const applied: string[] = [];

  for (const result of results) {
    if (!result.patchFile || result.status !== "success") {
      continue;
    }

    const command = await runCommand("git", ["apply", result.patchFile], repoPath);
    if (command.exitCode !== 0) {
      throw new Error(`Failed to apply ${result.patchFile}: ${command.stderr}`);
    }
    applied.push(result.patchFile);
  }

  return applied;
}
