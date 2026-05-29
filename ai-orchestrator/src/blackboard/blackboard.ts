import type { AgentRole } from "../domain/agent-task-spec.js";
import type { BlackboardEntry, BlackboardSnapshot } from "../domain/blackboard.js";

export class Blackboard {
  private entries = new Map<string, BlackboardEntry[]>();

  write(key: string, value: unknown, producerId: string, producerRole?: AgentRole): void {
    const entry: BlackboardEntry = {
      key,
      value,
      producerId,
      producerRole,
      timestamp: Date.now()
    };
    const existing = this.entries.get(key);
    if (existing) {
      existing.push(entry);
    } else {
      this.entries.set(key, [entry]);
    }
  }

  read(key: string): BlackboardEntry | undefined {
    const entries = this.entries.get(key);
    if (!entries || entries.length === 0) {
      return undefined;
    }
    return entries[entries.length - 1];
  }

  readAll(key: string): BlackboardEntry[] {
    return this.entries.get(key) ?? [];
  }

  getRelevantEntries(inputKeys: string[]): Record<string, unknown> {
    const context: Record<string, unknown> = {};
    for (const key of inputKeys) {
      const latest = this.read(key);
      if (latest) {
        context[key] = latest.value;
      }
    }
    return context;
  }

  getProducerEntries(producerId: string): Map<string, BlackboardEntry> {
    const result = new Map<string, BlackboardEntry>();
    for (const entries of this.entries.values()) {
      for (const entry of entries) {
        if (entry.producerId === producerId) {
          result.set(entry.key, entry);
        }
      }
    }
    return result;
  }

  snapshot(): BlackboardSnapshot {
    const snapshot: BlackboardSnapshot = {};
    for (const [key, entries] of this.entries) {
      snapshot[key] = [...entries];
    }
    return snapshot;
  }

  keys(): string[] {
    return Array.from(this.entries.keys());
  }
}
