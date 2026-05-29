import { z } from "zod";
import { AgentResultSchema } from "./agent-result.js";
import { AgentTaskSpecSchema } from "./agent-task-spec.js";
import { CiFailureEventSchema } from "./ci-event.js";

export const FailureTypeSchema = z.enum([
  "MAVEN_COMPILE_FAILURE",
  "UNIT_TEST_FAILURE",
  "DEPENDENCY_FAILURE",
  "ENVIRONMENT_FAILURE",
  "UNKNOWN_FAILURE"
]);

export const FailureAnalysisSchema = z.object({
  failureType: FailureTypeSchema,
  module: z.string().nullable(),
  failedTests: z.array(z.string()),
  summary: z.string(),
  confidence: z.number().min(0).max(1)
});

export const OrchestrationRunSchema = z.object({
  runId: z.string().min(1),
  event: CiFailureEventSchema,
  analysis: FailureAnalysisSchema,
  tasks: z.array(AgentTaskSpecSchema),
  results: z.array(AgentResultSchema),
  status: z.enum(["planned", "running", "verified", "needs-human", "failed"])
});

export type FailureType = z.infer<typeof FailureTypeSchema>;
export type FailureAnalysis = z.infer<typeof FailureAnalysisSchema>;
export type OrchestrationRun = z.infer<typeof OrchestrationRunSchema>;
