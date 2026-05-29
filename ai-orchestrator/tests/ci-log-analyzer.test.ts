import { describe, expect, it } from "vitest";
import { analyzeCiLog } from "../src/analyzer/ci-log-analyzer.js";

describe("analyzeCiLog", () => {
  it("classifies Maven compilation failures and extracts the module", () => {
    const log = [
      "[INFO] Building enterprise-knowledge-ai-service 1.0-SNAPSHOT",
      "[ERROR] COMPILATION ERROR :",
      "[ERROR] /workspace/enterprise-knowledge-ai-service/src/main/java/com/zjl/Foo.java:[12,8] cannot find symbol"
    ].join("\n");

    const analysis = analyzeCiLog(log, []);

    expect(analysis).toEqual({
      failureType: "MAVEN_COMPILE_FAILURE",
      module: "enterprise-knowledge-ai-service",
      failedTests: [],
      summary: "Maven compilation failed in enterprise-knowledge-ai-service.",
      confidence: 0.9
    });
  });

  it("classifies unit test failures using Surefire failures", () => {
    const log = "[ERROR] There are test failures.\n[ERROR] Please refer to target/surefire-reports";

    const analysis = analyzeCiLog(log, ["KbDocumentServiceImplTest.startChunkShouldMovePendingDocumentToRunning"]);

    expect(analysis.failureType).toBe("UNIT_TEST_FAILURE");
    expect(analysis.module).toBeNull();
    expect(analysis.failedTests).toEqual(["KbDocumentServiceImplTest.startChunkShouldMovePendingDocumentToRunning"]);
    expect(analysis.confidence).toBe(0.85);
  });
});
