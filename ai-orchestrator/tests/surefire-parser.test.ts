import { readFile } from "node:fs/promises";
import { describe, expect, it } from "vitest";
import { parseSurefireReport } from "../src/artifacts/surefire-parser.js";

describe("parseSurefireReport", () => {
  it("extracts failed test cases from a Surefire XML report", async () => {
    const xml = await readFile("examples/surefire-failure-report.xml", "utf8");

    const result = parseSurefireReport(xml);

    expect(result.totalTests).toBe(2);
    expect(result.failures).toHaveLength(1);
    expect(result.failures[0]).toEqual({
      className: "com.zjl.knowledge.service.KbDocumentServiceImplTest",
      testName: "startChunkShouldMovePendingDocumentToRunning",
      message: "expected: <RUNNING> but was: <FAILED>",
      type: "org.opentest4j.AssertionFailedError"
    });
  });
});
