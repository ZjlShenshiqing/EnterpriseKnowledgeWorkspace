import { mkdir, writeFile } from "node:fs/promises";
import { join } from "node:path";
import type { OrchestrationRun } from "../domain/orchestration-run.js";

export async function writePrSummaryArtifact(outputDir: string, run: OrchestrationRun): Promise<string> {
  await mkdir(outputDir, { recursive: true });
  const path = join(outputDir, `${run.runId}-pr-summary.md`);
  const body = [
    `# AI Repair PR Summary`,
    ``,
    `Run: ${run.runId}`,
    `Failure Type: ${run.analysis.failureType}`,
    `Module: ${run.analysis.module ?? "unknown"}`,
    ``,
    `## Summary`,
    run.analysis.summary,
    ``,
    `## Agent Results`,
    ...run.results.map((result) => `- ${result.agentName}: ${result.status} - ${result.summary}`)
  ].join("\n");

  await writeFile(path, body, "utf8");
  return path;
}
