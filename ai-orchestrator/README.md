# AI Orchestrator

Standalone TypeScript service for CI/CD AI Agent orchestration.

## MVP Scope

- Receive CI failure events.
- Classify Maven compile and unit-test failures.
- Plan multi-agent tasks.
- Run agents through runtime adapters.
- Keep writable agents isolated in git worktrees.
- Verify repair output before PR creation.

## Commands

```bash
npm install
npm test
npm run build
npm run dev
```

## Safety

The Orchestrator may create repair branches and PR summaries. It must not merge protected branches, deploy environments, read production secrets, or bypass CI.

## Webhook Example

```bash
curl -X POST http://localhost:8095/webhooks/ci-failure \
  -H 'Content-Type: application/json' \
  --data @examples/github-actions-failure-event.json
```

The MVP returns an orchestration run JSON. Production integration should download CI artifacts before invoking repair agents.
