import { z } from "zod";

export const CiProviderSchema = z.enum(["github-actions", "jenkins"]);

export const CiFailureEventSchema = z.object({
  eventId: z.string().min(1),
  provider: CiProviderSchema,
  repository: z.string().min(1),
  branch: z.string().min(1),
  commitSha: z.string().min(1),
  pullRequest: z.number().int().positive().optional(),
  workflow: z.string().min(1),
  job: z.string().min(1),
  status: z.literal("failure"),
  artifacts: z.object({
    ciLog: z.string().min(1),
    surefireReports: z.string().min(1).optional(),
    gitDiff: z.string().min(1).optional(),
    moduleInfo: z.string().min(1).optional(),
    dependencyTree: z.string().min(1).optional()
  }),
  triggeredAt: z.string().datetime({ offset: true })
});

export type CiFailureEvent = z.infer<typeof CiFailureEventSchema>;
