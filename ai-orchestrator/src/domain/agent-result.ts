import { z } from "zod";
import { AgentRoleSchema } from "./agent-task-spec.js";

export const AgentStatusSchema = z.enum(["success", "failed", "needs-human"]);
export const RiskLevelSchema = z.enum(["low", "medium", "high"]);

export const AgentResultSchema = z.object({
  agentName: AgentRoleSchema,
  status: AgentStatusSchema,
  summary: z.string().min(1),
  rootCause: z.string().optional(),
  changedFiles: z.array(z.string()),
  patchFile: z.string().nullable(),
  verificationCommand: z.string().nullable(),
  riskLevel: RiskLevelSchema,
  needsHumanReview: z.boolean()
});

export type AgentResult = z.infer<typeof AgentResultSchema>;
export type AgentStatus = z.infer<typeof AgentStatusSchema>;
export type RiskLevel = z.infer<typeof RiskLevelSchema>;
