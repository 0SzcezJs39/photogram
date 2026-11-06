# AGENTS.md

Photogram: photo feed with hashtags, likes, "who liked" list, stateless (JWT). Spec: `ts.txt` (Russian).
No workspace tooling, no CI, no lint/format/typecheck config anywhere — there is nothing to run beyond what is listed below.

## Layout

- `backend/` — Spring Boot 4.1.1, Java 21, Maven wrapper. Package `com.company.photogram` → `web/` (controllers), `service/`, `domain/` (entities + repositories).
- `frontend/` — Vue 3 + vue-router + Vite 6, plain JS (no TS, no store). `src/auth.js` holds token/auth helpers, `src/router.js` routes, `src/views/PhotosView.vue` is the feed.
- `docker-compose.yaml` (root) = full stack (mariadb, garage, backend, frontend). Инфру без бэкенда можно поднять отдельно: `docker compose up -d mariadb garage`.
- `docker_data/` (root) = bind-mount с данными инфраструктуры: `mariadb/` → `/var/lib/mysql`, `garage/` → `/etc/garage.toml` + `/var/lib/garage/{meta,data}`. Там же `backend/target*`, `frontend/node_modules/`, `frontend/dist/`, `.env` — всё это gitignored, никогда не коммитить. `.env.example` IS tracked and lists every required variable.

## Commands (all verified)

```bash
# backend build (Docker/CI path skips tests too)
cd backend && ./mvnw -B -DskipTests package

# backend tests — only contextLoads; needs live MariaDB + env vars (see below), else fails
cd backend && ./mvnw test

# frontend
cd frontend && npm run dev      # Vite on :5173, proxies /api -> http://localhost:8082
cd frontend && npm run build    # -> dist/
```

Full stack: `docker compose up -d --build` (root). First-time S3 bootstrap: `docker compose --profile init up -d` (one-shot `garage-init` creates key/bucket; idempotent).

There is no lint/format/typecheck command — do not add tooling unless asked.

## Architecture gotchas

- Backend serves under context-path **`/api`** (`application.properties`). Controller mappings are relative to it; real URLs are `/api/photos`, `/api/auth/login`, …
- Vite dev proxy must NOT strip `/api` (`vite.config.js` warns: rewriting gives 404s). Same for nginx: `proxy_pass http://backend:8082;` without trailing slash.
- Image bytes are proxied by the backend (`GET /api/photos/{id}/image`) so S3 credentials stay server-side; photo JSON stores that relative URL.
- Spring Data REST is on the classpath but disabled: repositories are `@RepositoryRestResource(exported = false)`; do not set `spring.data.rest.basePath` (yields `/api/api/...`).
- Schema is managed by `spring.jpa.hibernate.ddl-auto=update` — no Flyway/Liquibase; schema changes mean editing entities and restarting (it mutates the shared dev DB).
- S3 is Garage (not AWS): `aws.s3.endpoint=http://localhost:3900` locally, `AWS_S3_ENDPOINT=http://garage:3900` in compose (relaxed env binding).

## Security / API

- Stateless HS256 JWT (`Authorization: Bearer …`). Secret from `JWT_SECRET` / `app.jwt.secret`, ≥32 bytes. There is **no default**: if `JWT_SECRET` is unset the app fails to start and `docker compose` fails with a `:?` message. Generate one with `openssl rand -base64 48` into `.env`.
- Public only: `GET /api/photos`, `GET /api/photos/{id}/image`, `POST /api/auth/login`, `POST /api/auth/register`. Everything else (incl. `GET /api/photos/{id}/likes`, `GET /api/me`, `GET /api/users`) needs a JWT.
- Frontend stores the token in localStorage (`photogram_token`) and sends it via `authHeaders()` from `src/auth.js`; guarded routes use `meta.requiresAuth`.
- Tag handling: leading `#` is stripped, trimmed, deduped case-insensitively (`normalizeTags`/`normalizeTag` in `PhotoController`). Filter: `GET /api/photos?tag=…`.
- `README.md` holds the up-to-date API table and curl examples; `backend/my-curl-tests.txt` was removed (it was stale — basic auth `-u`, which the API no longer accepts).

## Environment & local-run quirks

- Config comes from env vars: `MYSQL_DATABASE`, `MYSQL_USER`, `MYSQL_PASSWORD`, `MYSQL_ROOT_PASSWORD`, `S3_ACCESS_KEY`, `S3_SECRET_KEY`, `S3_BUCKET_NAME`, `JWT_SECRET` — all defined in the root `.env`, which the dotenv dependency/compose read. Compose marks every one of them required (`${VAR:?…}`).
- Infra is usually already up (MariaDB :3306, backend :8082, Garage :3900, frontend :8080) — check ports before starting duplicates. Local `./mvnw test`/app start fails without the `MYSQL_*` env vars (`${MYSQL_USER}` unresolved) even if the DB is running, and also without `JWT_SECRET`.
- In sandboxed agent sessions: `.env` is mode 600 owned by another user and `/var/run/docker.sock` is root-only, so `docker compose …` and test runs may be impossible; the stack still answers on the ports above.
- `git` may refuse with "dubious ownership" (repo files owned by another user): `git config --global --add safe.directory <repo-path>`.
- `mvn` build can die with `FileSystemException: … Operation not permitted` on `backend/target/classes/…`: stale build artifacts owned by another user. `rm -rf backend/target` and rebuild (it's gitignored).
- `backend/Dockerfile` runs `mvn -B -DskipTests package`; `frontend/Dockerfile` runs `npm ci && npm run build` then serves `dist/` via nginx (`client_max_body_size 25m`, SPA history fallback).

## Conventions

- Code comments, UI copy, and commit messages are in Russian — match the surrounding language.
- Keep responses/API payloads unchanged in shape (`PhotoResponse` record) unless the task says otherwise.
