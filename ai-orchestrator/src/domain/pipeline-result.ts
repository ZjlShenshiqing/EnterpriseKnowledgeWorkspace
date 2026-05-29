import { z } from "zod";

export const PipelineResultSchema = z.object({
  patchesApplied: z.array(z.string()),
  verificationPassed: z.boolean(),
  verificationExitCode: z.number().nullable(),
  verificationStdout: z.string().default(""),
  verificationStderr: z.string().default(""),
  reviewGatePassed: z.boolean(),
  reviewGateReasons: z.array(z.string()),
  prSummaryPath: z.string().nullable()
});

export type PipelineResult = z.infer<typeof PipelineResultSchema>;
