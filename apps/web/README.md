# MeetStudent web

Public front of MeetStudent for students and experts: Angular 20 with SSR, Tailwind v4, Transloco (fr/en).
It talks to the API under `/api/v1` (see `proxy.conf.json` for local dev).

## Commands

Run from `apps/web`, with the API on `http://localhost:8080` (for example `docker compose up -d api` at the repo root).

```bash
npm install
npm start                                             # ng serve on http://localhost:4200
npm run build                                         # SSR build into dist/
npm run serve:ssr:frontend                            # serve the SSR build
npm test                                              # Karma + Jasmine
npm test -- --no-watch --browsers=ChromeHeadless      # as in CI
```

## More

- Agent guide and Angular/TypeScript rules: [.claude/CLAUDE.md](.claude/CLAUDE.md)
- API contract changes in flight: [docs/backend-api-integration.md](docs/backend-api-integration.md)
- Deployment: [docs/DEPLOY.md](../../docs/DEPLOY.md)
