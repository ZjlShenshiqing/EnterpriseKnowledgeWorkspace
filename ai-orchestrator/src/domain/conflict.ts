import { z } from "zod";

export const FileConflictSchema = z.object({
  filePath: z.string(),
  conflictingTaskIds: z.array(z.string()),
  conflictingRoles: z.array(z.string()),
  description: z.string()
});

export const FileConflictResolutionSchema = z.enum(["unresolved", "auto-merged", "flagged-for-human"]);

export type FileConflict = z.infer<typeof FileConflictSchema>;
export type FileConflictResolution = z.infer<typeof FileConflictResolutionSchema>;
