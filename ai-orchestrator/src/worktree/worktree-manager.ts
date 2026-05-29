import { mkdir } from "node:fs/promises";
import { simpleGit } from "simple-git";
import type { AgentTaskSpec } from "../domain/agent-task-spec.js";

export async function prepareWorktree(repoPath: string, baseBranch: string, task: AgentTaskSpec): Promise<string> {
  await mkdir(task.workspace, { recursive: true });
  const git = simpleGit(repoPath);
  const branchName = `ai/${task.taskId}`;
  await git.raw(["worktree", "add", "-B", branchName, task.workspace, baseBranch]);
  return task.workspace;
}
