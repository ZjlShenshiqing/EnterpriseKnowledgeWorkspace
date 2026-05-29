import type { FailureAnalysis } from "../domain/orchestration-run.js";
import { runCommand, type CommandResult } from "../utils/command-runner.js";

export function selectVerificationCommand(analysis: FailureAnalysis): string[] {
  if (analysis.module) {
    return ["mvn", "test", "-pl", analysis.module];
  }
  return ["mvn", "test"];
}

export async function runVerification(repoPath: string, analysis: FailureAnalysis): Promise<CommandResult> {
  const [command, ...args] = selectVerificationCommand(analysis);
  return runCommand(command, args, repoPath);
}
