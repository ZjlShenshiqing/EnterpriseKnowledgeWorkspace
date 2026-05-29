import type { AgentResult } from "../domain/agent-result.js";
import type { FileConflict } from "../domain/conflict.js";

interface FileOwner {
  taskId: string;
  role: string;
}

export class ConflictDetector {
  detect(results: AgentResult[]): FileConflict[] {
    const fileOwners = new Map<string, FileOwner[]>();

    for (const result of results) {
      for (const file of result.changedFiles) {
        const owners = fileOwners.get(file);
        if (owners) {
          const alreadyRecorded = owners.some((o) => o.taskId === result.agentName);
          if (!alreadyRecorded) {
            owners.push({ taskId: result.agentName, role: result.agentName });
          }
        } else {
          fileOwners.set(file, [{ taskId: result.agentName, role: result.agentName }]);
        }
      }
    }

    const conflicts: FileConflict[] = [];
    for (const [filePath, owners] of fileOwners) {
      if (owners.length > 1) {
        conflicts.push({
          filePath,
          conflictingTaskIds: owners.map((o) => o.taskId),
          conflictingRoles: owners.map((o) => o.role),
          description: `${owners.map((o) => o.role).join(" and ")} both modified ${filePath}`
        });
      }
    }

    return conflicts;
  }
}
