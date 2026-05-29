import type { AgentResult } from "../domain/agent-result.js";

export interface ReviewGateResult {
  passed: boolean;
  reasons: string[];
}

const forbiddenPaths = [
  "application-secrets.yml",
  ".env",
  "id_rsa",
  "credentials"
];

export function evaluateReviewGate(results: AgentResult[]): ReviewGateResult {
  const reasons: string[] = [];

  for (const result of results) {
    for (const file of result.changedFiles) {
      if (forbiddenPaths.some((path) => file.includes(path))) {
        reasons.push(`Forbidden file changed: ${file}`);
      }
    }

    if (result.riskLevel === "high") {
      reasons.push(`High risk result from ${result.agentName}: ${result.summary}`);
    }
  }

  return {
    passed: reasons.length === 0,
    reasons
  };
}
