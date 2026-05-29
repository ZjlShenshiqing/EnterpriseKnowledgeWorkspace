import { execa } from "execa";

export interface CommandResult {
  exitCode: number;
  stdout: string;
  stderr: string;
}

export async function runCommand(
  command: string,
  args: string[],
  cwd: string
): Promise<CommandResult> {
  try {
    const result = await execa(command, args, {
      cwd,
      reject: false,
      all: false
    });

    return {
      exitCode: result.exitCode ?? 0,
      stdout: result.stdout,
      stderr: result.stderr
    };
  } catch (error) {
    const message = error instanceof Error ? error.message : String(error);
    return {
      exitCode: 1,
      stdout: "",
      stderr: message
    };
  }
}
