import { readFile } from "node:fs/promises";
import { parseSurefireReport } from "./surefire-parser.js";

export async function loadTextArtifact(path: string): Promise<string> {
  return readFile(path, "utf8");
}

export async function loadFailedTestsFromSurefire(path: string): Promise<string[]> {
  const xml = await readFile(path, "utf8");
  const report = parseSurefireReport(xml);
  return report.failures.map((failure) => `${failure.className}.${failure.testName}`);
}
