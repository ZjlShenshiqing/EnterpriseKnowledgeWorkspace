import type { FailureAnalysis } from "../domain/orchestration-run.js";

const modulePattern = /\[INFO\] Building ([a-zA-Z0-9_.-]+) /;

export function analyzeCiLog(log: string, failedTests: string[]): FailureAnalysis {
  const moduleMatch = log.match(modulePattern);
  const moduleName = moduleMatch?.[1] ?? null;

  if (log.includes("COMPILATION ERROR")) {
    return {
      failureType: "MAVEN_COMPILE_FAILURE",
      module: moduleName,
      failedTests: [],
      summary: moduleName ? `Maven compilation failed in ${moduleName}.` : "Maven compilation failed.",
      confidence: 0.9
    };
  }

  if (log.includes("There are test failures") || failedTests.length > 0) {
    return {
      failureType: "UNIT_TEST_FAILURE",
      module: moduleName,
      failedTests,
      summary: failedTests.length > 0
        ? `Unit tests failed: ${failedTests.join(", ")}.`
        : "Maven unit tests failed.",
      confidence: 0.85
    };
  }

  if (log.includes("Could not resolve dependencies") || log.includes("DependencyResolutionException")) {
    return {
      failureType: "DEPENDENCY_FAILURE",
      module: moduleName,
      failedTests: [],
      summary: "Maven dependency resolution failed.",
      confidence: 0.8
    };
  }

  return {
    failureType: "UNKNOWN_FAILURE",
    module: moduleName,
    failedTests,
    summary: "CI failure type could not be classified by MVP rules.",
    confidence: 0.3
  };
}
