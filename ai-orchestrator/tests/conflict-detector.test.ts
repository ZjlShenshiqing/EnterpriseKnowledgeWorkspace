import { describe, expect, it } from "vitest";
import { ConflictDetector } from "../src/conflict/conflict-detector.js";
import type { AgentResult } from "../src/domain/agent-result.js";

function makeResult(agentName: string, changedFiles: string[]): AgentResult {
  return {
    agentName: agentName as AgentResult["agentName"],
    status: "success",
    summary: "ok",
    changedFiles,
    patchFile: null,
    verificationCommand: null,
    riskLevel: "low",
    needsHumanReview: false
  };
}

describe("ConflictDetector", () => {
  const detector = new ConflictDetector();

  it("returns empty conflicts when no agents modified files", () => {
    const conflicts = detector.detect([]);
    expect(conflicts).toHaveLength(0);
  });

  it("returns empty conflicts when single agent modifies files", () => {
    const conflicts = detector.detect([
      makeResult("build-fix-agent", ["src/Foo.java"])
    ]);
    expect(conflicts).toHaveLength(0);
  });

  it("returns empty conflicts when two agents modify different files", () => {
    const conflicts = detector.detect([
      makeResult("code-fix-agent", ["src/Foo.java"]),
      makeResult("test-writer-agent", ["src/FooTest.java"])
    ]);
    expect(conflicts).toHaveLength(0);
  });

  it("detects conflict when two agents modify the same file", () => {
    const conflicts = detector.detect([
      makeResult("code-fix-agent", ["src/Foo.java"]),
      makeResult("test-writer-agent", ["src/Foo.java"])
    ]);
    expect(conflicts).toHaveLength(1);
    expect(conflicts[0].filePath).toBe("src/Foo.java");
    expect(conflicts[0].conflictingTaskIds.sort()).toEqual(
      ["code-fix-agent", "test-writer-agent"].sort()
    );
  });

  it("detects conflict when three agents modify the same file", () => {
    const conflicts = detector.detect([
      makeResult("code-fix-agent", ["src/Foo.java"]),
      makeResult("test-writer-agent", ["src/Foo.java"]),
      makeResult("build-fix-agent", ["src/Foo.java"])
    ]);
    expect(conflicts).toHaveLength(1);
    expect(conflicts[0].conflictingTaskIds).toHaveLength(3);
  });

  it("detects multiple conflicting files", () => {
    const conflicts = detector.detect([
      makeResult("code-fix-agent", ["src/Foo.java", "src/Bar.java"]),
      makeResult("test-writer-agent", ["src/Bar.java", "src/Baz.java"])
    ]);
    expect(conflicts).toHaveLength(1);
    expect(conflicts[0].filePath).toBe("src/Bar.java");
  });

  it("deduplicates same agent reporting same file", () => {
    const conflicts = detector.detect([
      makeResult("code-fix-agent", ["src/Foo.java", "src/Foo.java"])
    ]);
    expect(conflicts).toHaveLength(0);
  });
});
