import { z } from "zod";

const ConfigSchema = z.object({
  port: z.number().int().positive(),
  workspaceRoot: z.string().min(1),
  defaultRuntime: z.enum(["mock", "codex", "claude", "gemini", "custom"]),
  maxRepairAttempts: z.number().int().positive(),
  codexCliCommand: z.string().min(1)
});

export type OrchestratorConfig = z.infer<typeof ConfigSchema>;

export function loadConfig(env: NodeJS.ProcessEnv = process.env): OrchestratorConfig {
  return ConfigSchema.parse({
    port: Number(env.ORCHESTRATOR_PORT ?? 8095),
    workspaceRoot: env.ORCHESTRATOR_WORKSPACE_ROOT ?? "/tmp/enterprise-ai-orchestrator",
    defaultRuntime: env.ORCHESTRATOR_DEFAULT_RUNTIME ?? "mock",
    maxRepairAttempts: Number(env.ORCHESTRATOR_MAX_REPAIR_ATTEMPTS ?? 2),
    codexCliCommand: env.CODEX_CLI_COMMAND ?? "codex"
  });
}
