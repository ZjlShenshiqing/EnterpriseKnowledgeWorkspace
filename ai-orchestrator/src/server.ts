import Fastify from "fastify";
import { CodexCliAdapter } from "./adapters/codex-cli-adapter.js";
import { MockAgentAdapter } from "./adapters/mock-agent-adapter.js";
import { loadConfig } from "./config.js";
import { CiFailureEventSchema } from "./domain/ci-event.js";
import { runOrchestration } from "./orchestrator.js";

export async function buildServer() {
  const config = loadConfig();
  const server = Fastify({ logger: true });

  server.get("/health", async () => ({ status: "ok" }));

  server.post("/webhooks/ci-failure", async (request, reply) => {
    const parsed = CiFailureEventSchema.safeParse(request.body);
    if (!parsed.success) {
      return reply.status(400).send(parsed.error.message);
    }

    const run = await runOrchestration({
      event: parsed.data,
      ciLog: "MVP webhook received. Artifact download is handled by CI artifact preparation.",
      failedTests: [],
      runtime: config.defaultRuntime,
      workspaceRoot: config.workspaceRoot,
      adapters: {
        mock: new MockAgentAdapter(),
        codex: new CodexCliAdapter({ command: config.codexCliCommand })
      }
    });

    return reply.send(run);
  });

  return server;
}
