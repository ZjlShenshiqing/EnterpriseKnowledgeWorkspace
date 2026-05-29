import type { AgentTaskSpec } from "../domain/agent-task-spec.js";
import type { ExecutionWave, SchedulingResult } from "./types.js";

export class DagScheduler {
  schedule(tasks: AgentTaskSpec[]): SchedulingResult {
    if (tasks.length === 0) {
      return { waves: [], unscheduled: [] };
    }

    const taskMap = new Map<string, AgentTaskSpec>();
    const adjacency = new Map<string, string[]>();
    const inDegree = new Map<string, number>();

    for (const task of tasks) {
      taskMap.set(task.taskId, task);
      if (!inDegree.has(task.taskId)) {
        inDegree.set(task.taskId, 0);
      }
    }

    for (const task of tasks) {
      for (const depId of task.dependencies) {
        if (!taskMap.has(depId)) {
          continue;
        }
        const children = adjacency.get(depId);
        if (children) {
          children.push(task.taskId);
        } else {
          adjacency.set(depId, [task.taskId]);
        }
        inDegree.set(task.taskId, (inDegree.get(task.taskId) ?? 0) + 1);
      }
    }

    const queue: string[] = [];
    for (const [taskId, degree] of inDegree) {
      if (degree === 0) {
        queue.push(taskId);
      }
    }

    const waves: ExecutionWave[] = [];
    let level = 0;

    while (queue.length > 0) {
      const currentWave: string[] = [...queue];
      queue.length = 0;

      const waveTasks: AgentTaskSpec[] = [];
      for (const taskId of currentWave) {
        const task = taskMap.get(taskId);
        if (task) {
          waveTasks.push(task);
        }
      }

      waves.push({ level, tasks: waveTasks });
      level++;

      for (const taskId of currentWave) {
        const children = adjacency.get(taskId);
        if (!children) continue;
        for (const childId of children) {
          const newDegree = (inDegree.get(childId) ?? 1) - 1;
          inDegree.set(childId, newDegree);
          if (newDegree === 0) {
            queue.push(childId);
          }
        }
      }
    }

    const unscheduled: string[] = [];
    for (const [taskId, degree] of inDegree) {
      if (degree > 0) {
        unscheduled.push(taskId);
      }
    }

    return { waves, unscheduled };
  }
}
