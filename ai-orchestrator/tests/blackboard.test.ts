import { describe, expect, it } from "vitest";
import { Blackboard } from "../src/blackboard/blackboard.js";

describe("Blackboard", () => {
  it("writes and reads a single entry", () => {
    const bb = new Blackboard();
    bb.write("test:key", { data: 42 }, "agent-1", "context-agent");

    const entry = bb.read("test:key");
    expect(entry).toBeDefined();
    expect(entry!.value).toEqual({ data: 42 });
    expect(entry!.producerId).toBe("agent-1");
  });

  it("returns undefined for unknown key", () => {
    const bb = new Blackboard();
    expect(bb.read("nonexistent")).toBeUndefined();
  });

  it("returns latest value for key with multiple entries", () => {
    const bb = new Blackboard();
    bb.write("test:key", "v1", "agent-1");
    bb.write("test:key", "v2", "agent-2");

    const entry = bb.read("test:key");
    expect(entry!.value).toBe("v2");
  });

  it("readAll returns all versions of a key", () => {
    const bb = new Blackboard();
    bb.write("test:key", "v1", "agent-1");
    bb.write("test:key", "v2", "agent-2");

    const all = bb.readAll("test:key");
    expect(all).toHaveLength(2);
    expect(all[0].value).toBe("v1");
    expect(all[1].value).toBe("v2");
  });

  it("getRelevantEntries returns only requested keys", () => {
    const bb = new Blackboard();
    bb.write("a:key", 1, "agent-1");
    bb.write("b:key", 2, "agent-2");
    bb.write("c:key", 3, "agent-3");

    const ctx = bb.getRelevantEntries(["a:key", "c:key"]);
    expect(ctx).toEqual({ "a:key": 1, "c:key": 3 });
  });

  it("getRelevantEntries skips missing keys", () => {
    const bb = new Blackboard();
    bb.write("a:key", 1, "agent-1");

    const ctx = bb.getRelevantEntries(["a:key", "missing:key"]);
    expect(ctx).toEqual({ "a:key": 1 });
  });

  it("getProducerEntries filters by producer", () => {
    const bb = new Blackboard();
    bb.write("a:key", 1, "agent-1");
    bb.write("b:key", 2, "agent-1");
    bb.write("c:key", 3, "agent-2");

    const entries = bb.getProducerEntries("agent-1");
    expect(entries.size).toBe(2);
    expect(entries.get("a:key")!.value).toBe(1);
    expect(entries.get("b:key")!.value).toBe(2);
  });

  it("snapshot captures full state", () => {
    const bb = new Blackboard();
    bb.write("a:key", 1, "agent-1");
    bb.write("a:key", 2, "agent-2");
    bb.write("b:key", "x", "agent-1");

    const snap = bb.snapshot();
    expect(snap["a:key"]).toHaveLength(2);
    expect(snap["b:key"]).toHaveLength(1);
  });

  it("keys returns all distinct keys", () => {
    const bb = new Blackboard();
    bb.write("a:key", 1, "agent-1");
    bb.write("b:key", 2, "agent-2");

    const keys = bb.keys();
    expect(keys.sort()).toEqual(["a:key", "b:key"]);
  });

  it("entries have timestamps", () => {
    const before = Date.now();
    const bb = new Blackboard();
    bb.write("test:key", "value", "agent-1");
    const after = Date.now();

    const entry = bb.read("test:key")!;
    expect(entry.timestamp).toBeGreaterThanOrEqual(before);
    expect(entry.timestamp).toBeLessThanOrEqual(after);
  });
});
