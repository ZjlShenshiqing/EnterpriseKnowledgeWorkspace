import { describe, expect, it } from "vitest";
import type { AgentTaskSpec } from "../src/domain/agent-task-spec.js";
import { DagScheduler } from "../src/scheduler/dag-scheduler.js";

function makeTask(
  id: string,
  deps: string[] = []
): AgentTaskSpec {
  return {
    taskId: id,
    role: "context-agent",
    runtime: "mock",
    workspace: `/tmp/${id}`,
    inputArtifacts: {},
    repoContext: [],
    constraints: [],
    expectedOutput: { type: "analysis-report", format: "json" },
    readOnly: true,
    dependencies: deps,
    inputKeys: [],
    outputKeys: [],
    blackboardContext: {}
  };
}

describe("DagScheduler", () => {
  const scheduler = new DagScheduler();

  it("returns empty waves for empty task list", () => {
    const result = scheduler.schedule([]);
    expect(result.waves).toHaveLength(0);
    expect(result.unscheduled).toHaveLength(0);
  });

  it("schedules single task in one wave", () => {
    const result = scheduler.schedule([makeTask("A")]);
    expect(result.waves).toHaveLength(1);
    expect(result.waves[0].tasks.map((t) => t.taskId)).toEqual(["A"]);
  });

  it("schedules two independent tasks in same wave", () => {
    const result = scheduler.schedule([makeTask("A"), makeTask("B")]);
    expect(result.waves).toHaveLength(1);
    expect(result.waves[0].tasks.map((t) => t.taskId).sort()).toEqual(["A", "B"]);
  });

  it("schedules linear chain A->B->C as three waves", () => {
    const result = scheduler.schedule([
      makeTask("A"),
      makeTask("B", ["A"]),
      makeTask("C", ["B"])
    ]);
    expect(result.waves).toHaveLength(3);
    expect(result.waves[0].tasks.map((t) => t.taskId)).toEqual(["A"]);
    expect(result.waves[1].tasks.map((t) => t.taskId)).toEqual(["B"]);
    expect(result.waves[2].tasks.map((t) => t.taskId)).toEqual(["C"]);
  });

  it("schedules diamond A->B, A->C, B->D, C->D as three waves", () => {
    const result = scheduler.schedule([
      makeTask("A"),
      makeTask("B", ["A"]),
      makeTask("C", ["A"]),
      makeTask("D", ["B", "C"])
    ]);
    expect(result.waves).toHaveLength(3);
    expect(result.waves[0].tasks.map((t) => t.taskId)).toEqual(["A"]);
    expect(result.waves[1].tasks.map((t) => t.taskId).sort()).toEqual(["B", "C"]);
    expect(result.waves[2].tasks.map((t) => t.taskId)).toEqual(["D"]);
  });

  it("detects circular dependency", () => {
    const result = scheduler.schedule([
      makeTask("A", ["B"]),
      makeTask("B", ["A"])
    ]);
    expect(result.unscheduled.length).toBeGreaterThan(0);
    expect(result.unscheduled.sort()).toEqual(["A", "B"]);
  });

  it("handles dependency on non-existent task gracefully", () => {
    const result = scheduler.schedule([
      makeTask("A", ["NONEXISTENT"]),
      makeTask("B")
    ]);
    expect(result.waves[0].tasks.map((t) => t.taskId).sort()).toEqual(["A", "B"]);
    expect(result.unscheduled).toHaveLength(0);
  });

  it("mixed: one independent root, two dependents, one of them also dependent on root", () => {
    const result = scheduler.schedule([
      makeTask("root"),
      makeTask("A", ["root"]),
      makeTask("B", ["A", "root"])
    ]);
    expect(result.waves[0].tasks.map((t) => t.taskId)).toEqual(["root"]);
    expect(result.waves[1].tasks.map((t) => t.taskId)).toEqual(["A"]);
    expect(result.waves[2].tasks.map((t) => t.taskId)).toEqual(["B"]);
  });
});
