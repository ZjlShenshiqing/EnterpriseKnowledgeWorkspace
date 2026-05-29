import { loadConfig } from "./config.js";
import { buildServer } from "./server.js";

const config = loadConfig();
const server = await buildServer();

await server.listen({
  port: config.port,
  host: "0.0.0.0"
});
