import { applyTrustedPatches } from "../patch/patch-merger.js";
import { evaluateReviewGate } from "../review/review-gate.js";
import { runVerification } from "../verification/verification-runner.js";
import type { AgentResult } from "../domain/agent-result.js";
import type { FailureAnalysis } from "../domain/orchestration-run.js";
import type { PipelineResult } from "../domain/pipeline-result.js";
import type { OrchestrationRun } from "../domain/orchestration-run.js";
import { writePrSummaryArtifact } from "../pr/pr-creator.js";

export interface PipelineInput {
  repoPath: string;
  analysis: FailureAnalysis;
  results: AgentResult[];
  run: OrchestrationRun;
  prOutputDir: string;
}

export async function runPipeline(input: PipelineInput): Promise<PipelineResult> {
  let patchesApplied: string[] = [];
  let verificationExitCode: number | null = null;
  let verificationStdout = "";
  let verificationStderr = "";
  let verificationPassed = true;

  try {
    patchesApplied = await applyTrustedPatches(input.repoPath, input.results);
  } catch {
    verificationPassed = false;
  }

  if (patchesApplied.length > 0) {
    try {
      const verification = await runVerification(input.repoPath, input.analysis);
      verificationExitCode = verification.exitCode;
      verificationStdout = verification.stdout;
      verificationStderr = verification.stderr;
      verificationPassed = verification.exitCode === 0;
    } catch {
      verificationPassed = false;
    }
  }

  const reviewGate = evaluateReviewGate(input.results);

  let prSummaryPath: string | null = null;
  try {
    prSummaryPath = await writePrSummaryArtifact(input.prOutputDir, input.run);
  } catch {
    /** PR summary is best-effort */
  }

  return {
    patchesApplied,
    verificationPassed,
    verificationExitCode,
    verificationStdout,
    verificationStderr,
    reviewGatePassed: reviewGate.passed,
    reviewGateReasons: reviewGate.reasons,
    prSummaryPath
  };
}
