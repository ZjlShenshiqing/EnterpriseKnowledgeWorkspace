import { z } from "zod";

export const AgentRoleSchema = z.enum([
  "ci-log-analyzer",
  "context-agent",
  "build-fix-agent",
  "test-analysis-agent",
  "code-fix-agent",
  "test-writer-agent",
  "pr-writer-agent",
  "review-agent"
]);

export const AgentRuntimeSchema = z.enum(["mock", "codex", "claude", "gemini", "custom"]);

export const ExpectedOutputSchema = z.object({
  type: z.enum(["analysis-report", "patch", "review", "pr-summary"]),
  format: z.enum(["json", "markdown"])
});

export const AgentTaskSpecSchema = z.object({
  taskId: z.string().min(1),
  role: AgentRoleSchema,
  runtime: AgentRuntimeSchema,
  workspace: z.string().min(1),
  inputArtifacts: z.record(z.string()),
  repoContext: z.array(z.string()),
  constraints: z.array(z.string()),
  expectedOutput: ExpectedOutputSchema,
  readOnly: z.boolean(),
  dependencies: z.array(z.string()).default([]),
  inputKeys: z.array(z.string()).default([]),
  outputKeys: z.array(z.string()).default([]),
  blackboardContext: z.record(z.unknown()).default({})
});

export type AgentRole = z.infer<typeof AgentRoleSchema>;
export type AgentRuntime = z.infer<typeof AgentRuntimeSchema>;
export type AgentTaskSpec = z.infer<typeof AgentTaskSpecSchema>;
