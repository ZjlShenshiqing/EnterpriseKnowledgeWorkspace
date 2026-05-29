import { z } from "zod";
import { AgentRoleSchema } from "./agent-task-spec.js";

export const BlackboardEntrySchema = z.object({
  key: z.string(),
  value: z.unknown(),
  producerId: z.string(),
  producerRole: AgentRoleSchema.optional(),
  timestamp: z.number()
});

export const BlackboardSnapshotSchema = z.record(
  z.string(),
  z.array(BlackboardEntrySchema)
);

export type BlackboardEntry = z.infer<typeof BlackboardEntrySchema>;
export type BlackboardSnapshot = z.infer<typeof BlackboardSnapshotSchema>;
