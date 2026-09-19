# Farm2Home CI/CD

GitHub Actions pipeline for the Farm2Home Milk platform. This document is the
source of truth for how CI/CD works, which commands run, what secrets are
needed, and how to recover from a bad deploy.

---

## 1. Architecture

```
Feature branch ──► Pull Request
                        │
        ┌───────────────┼────────────────┬─────────────────┐
        ▼               ▼                ▼                 ▼
   ci.yml/backend   ci.yml/frontend   docker.yml      security.yml
   Java 21 / Maven  Node 20 / npm     build only      CodeQL (Java + JS/TS)
   clean verify     lint              (no publish      dependency-review
   (unit + IT +     type-check         on PRs)
    JaCoCo gate)    test
                    build
        └───────────────┴────────────────┴─────────────────┘
                        │  all required checks green
                        ▼
                     Review + Merge to main
                        │
        ┌───────────────┴───────────────┐
        ▼                               ▼
   ci.yml + security.yml         docker.yml (push)
   re-run on main                build every image
                                 publish to GHCR:
                                   <service>:<git-sha>   (immutable)
                                   <service>:main        (moving)
                        │
                        ▼
              ┌──────────────────────┐
              │  CD — NOT CONFIGURED │  see §9
              └──────────────────────┘
```

Backend and frontend never block each other — they are separate jobs on
separate runners.

---

## 2. Triggers & branches

The repository has a single long-lived branch: **`main`**. Work happens on
`feature/*`, `fix/*`, `compliance/*` branches merged via PR. There is no
`develop` branch, so CI triggers on:

- `pull_request` (any branch → `main`)
- `push` to `main`
- `security.yml` also runs on a weekly `cron`

**Concurrency:** superseded runs on the same PR are cancelled
(`cancel-in-progress` is true only for `pull_request`). Runs on `main` always
finish, because each `main` commit is a release candidate whose images must be
published.

---

## 3. Backend CI (`ci.yml` → job `backend`)

| Step | Command | Notes |
|---|---|---|
| JDK | `actions/setup-java@v4` temurin 21, `cache: maven` | Java version from `backend/pom.xml` → `<java.version>21</java.version>` |
| Build + test + verify | `mvn -B --no-transfer-progress clean verify` (in `backend/`) | Runs from the aggregator `backend/pom.xml` across all 17 modules |
| Test report | `dorny/test-reporter` on `backend/*/target/surefire-reports/TEST-*.xml` | non-blocking annotation only |
| Artifacts (on failure) | surefire + failsafe + JaCoCo HTML | 7-day retention |

**What `mvn verify` covers here:**

- **Unit / slice tests** — Mockito + `@WebMvcTest` across every service.
- **Integration tests** — `*IntegrationTest` and `*ApplicationTests` extend a
  Testcontainers `PostgreSQLContainer` (`org.testcontainers:postgresql`,
  `1.20.1`). Docker is preinstalled on `ubuntu-latest`, so these run for real.
  *No GitHub Actions `services:` Postgres is needed* — Testcontainers manages
  its own container per test class (`postgres:15-alpine` / `postgres:16-alpine`).
- **Flyway migrations** — executed by Spring Boot against the Testcontainers
  database during integration tests (`spring.flyway.enabled=true`,
  `ddl-auto=validate`), so a broken migration fails CI.
- **Static analysis / coverage gate** — the JaCoCo `check` execution in
  `backend/pom.xml` is bound to `verify` and enforces **80 % line coverage per
  bundle** (with the exclusions listed in the pom). A module below 80 % fails
  the build. This is a pre-existing project gate, not added by CI.

> The retired `.gitlab-ci.yml` ran only `mvn test` (skipping `verify`), so the
> JaCoCo gate and any Flyway-at-verify checks were never enforced in CI before.
> If a module now fails on coverage, the fix is **more tests**, never lowering
> the threshold or adding `-DskipTests` / `continue-on-error`.

**Local equivalent:**

```bash
cd backend
mvn -B clean verify          # needs a running Docker daemon for the IT suite
```

---

## 4. Frontend CI (`ci.yml` → job `frontend`)

Package manager: **npm** (`frontend/package-lock.json` present; no yarn/pnpm
lockfile). Node: **20** — defined by `frontend/.nvmrc`, matching
`frontend/Dockerfile` (`node:20-alpine`) and the previous GitLab config.

| Step | Command |
|---|---|
| Install | `npm ci` |
| Lint | `npm run lint`  → `eslint src --ext ts,tsx --report-unused-disable-directives --max-warnings 0` |
| Type-check | `npx tsc --noEmit` |
| Unit tests | `npm test` → `vitest run --passWithNoTests` |
| Build | `npm run build` → `tsc && vite build` (output: `frontend/dist/`) |
| Artifact | `frontend/dist` uploaded (7-day retention) |

**Repository fixes made so these commands actually work** (the frontend had
never been linted or tested):

| File | Why |
|---|---|
| `frontend/.eslintrc.cjs` (new) | `npm run lint` errored with *"ESLint couldn't find a configuration file"*. Config uses the already-installed `@typescript-eslint` + `react-hooks` plugins. `@typescript-eslint/no-explicit-any` is **off** (78 pre-existing hits — tracked as tech debt, not a CI blocker); `no-unused-vars` honours the `^_` prefix the code already uses. |
| `frontend/src/test/setup.ts` (new) | `vite.config.ts` referenced `test.setupFiles: './src/test/setup.ts'` which did not exist. |
| `frontend/package.json` | `test` / `test:coverage` scripts gained `--passWithNoTests` — there are currently **zero** test files, and Vitest exits `1` on an empty suite. The runner is wired and ready; add specs under `src/**/*.test.tsx`. |
| `frontend/.nvmrc` (new) | Pins Node 20 as the single source of truth for CI, Docker and local `nvm`. |

**Local equivalent:**

```bash
cd frontend
npm ci && npm run lint && npx tsc --noEmit && npm test && npm run build
```

---

## 5. Docker (`docker.yml`)

Builds an image for **every backend service that has a Dockerfile** plus the
frontend:

```
api-gateway  auth-service  config-server  customer-service  dashboard-service
delivery-service  discovery-service  farm-service  inventory-service
notification-service  order-service  payment-service  production-service
reports-service  subscription-service        + frontend
```

> `backend/invoice-service` has **no Dockerfile** and is therefore not built or
> published. Add `backend/invoice-service/Dockerfile` (copy any sibling — they
> are identical apart from `EXPOSE`) to include it.

**Backend images are not multi-stage** — each Dockerfile is just
`FROM eclipse-temurin:21-jre-alpine` + `COPY target/*.jar app.jar`. The
workflow therefore runs `mvn -B -DskipTests clean package` first, then
`docker buildx build` per service. The frontend Dockerfile *is* multi-stage
(`node:20-alpine` build → `nginx:alpine`) and self-contained.

| Event | Behaviour |
|---|---|
| `pull_request` | Build all images (`--output type=docker`). **Nothing is pushed.** No GHCR login. |
| `push` to `main` | Build + push to GHCR. |
| `workflow_dispatch` | Same as a `main` push. |

**Image names & tags** (GHCR, owner l-cased automatically):

```
ghcr.io/akhilraju363/farm2home/<service>:<git-sha>   # immutable — use for deploys/rollback
ghcr.io/akhilraju363/farm2home/<service>:main        # moving — latest main build
```

`GITHUB_TOKEN` (auto-provisioned, `packages: write`) is the only credential —
no long-lived registry PAT. First publish of each package is **private**;
make them public or grant the deploy environment read access in
*Settings → Packages*.

**Local equivalent:**

```bash
cd backend && mvn -B -DskipTests clean package && cd ..
docker build -t farm2home/auth-service:dev backend/auth-service
docker build -t farm2home/frontend:dev     frontend
```

---

## 6. Security (`security.yml`)

| Job | Detail |
|---|---|
| `codeql` (`java-kotlin`) | `build-mode: manual` → `mvn -DskipTests clean package`, then analyze. |
| `codeql` (`javascript-typescript`) | `build-mode: none` (no build needed). |
| `dependency-review` | PR-only. Fails the PR on **High/Critical** advisories in changed manifests; lower severities are informational. |

Results appear under the repo **Security** tab. CodeQL findings do **not**
block merges unless you add *"CodeQL"* as a required status check (§8).

**Dependabot** (`.github/dependabot.yml`) opens weekly PRs for Maven
(`/backend`), npm (`/frontend`), `github-actions`, and the Docker base images.
Spring and MUI/React updates are grouped to reduce PR noise.

`npm ci` currently reports **17 advisories (2 critical, 10 high, 5 moderate)**
in the frontend tree and `mvn` may report transitive CVEs on the backend —
triage these via the Dependabot PRs; they are pre-existing and out of scope for
the CI wiring itself.

---

## 7. Secrets & configuration

### GitHub Secrets required

**None** for CI, Docker publishing or security scanning — everything uses the
built-in `GITHUB_TOKEN`.

Secrets become necessary only when CD is implemented (§9). Anticipated names
(declare in *Settings → Environments*, never in a file):

```
# staging / production environments, once a target exists
SSH_PRIVATE_KEY            # or a cloud provider's deploy credential
DEPLOY_HOST
DEPLOY_USER
JWT_SECRET                 # 256-bit base64 — overrides the committed dev default
POSTGRES_PASSWORD
EUREKA_PASSWORD
CONFIG_PASSWORD
# optional integrations (all default to no-op providers)
GOOGLE_CLIENT_ID  MAIL_USERNAME  MAIL_PASSWORD
RAZORPAY_KEY_ID  RAZORPAY_KEY_SECRET  RAZORPAY_WEBHOOK_SECRET
```

### Secret hygiene audit (findings — pre-existing, not changed by this work)

| Item | Status |
|---|---|
| `.env`, `*.env`, `application-local.yml`, `application-secret.yml` | ✅ git-ignored; only `.env.example` / `frontend/.env.example` are tracked (placeholders only) |
| No `.env` file is tracked | ✅ verified with `git ls-files` |
| `spring.datasource.password: farm2home@123` in every service's **base** `application.yml` | ⚠️ hardcoded **local-dev** default. `application-docker.yml` (the profile used in containers) parameterises it via env. **Recommendation:** change the base value to `${DB_PASSWORD:farm2home@123}` for consistency. |
| `jwt.secret: ${JWT_SECRET:ZmFy...}` in `auth-service` / `api-gateway` | ⚠️ committed **fallback** secret. Fine for local; **production must set `JWT_SECRET`**. Consider removing the default so a missing value fails fast. |
| `application-test.yml` test JWT secret | ✅ clearly test-only, never used at runtime |
| Workflows print no secret values; artifacts exclude `.env` / config | ✅ |

No secret values are reproduced in this document or in any workflow file.

### GitHub Environments

Not created yet (no deploy target). When CD lands, create:

- **`staging`** — auto-deploy on `main`, no reviewers.
- **`production`** — **required reviewers** + optional wait timer; restrict to
  the `main` branch. Production secrets live here, not in the repo.

---

## 8. Branch protection (apply manually — not automated)

*Settings → Branches → Add rule* for `main`:

- ✅ Require a pull request before merging (≥ 1 approval)
- ✅ Dismiss stale approvals on new commits
- ✅ Require status checks to pass before merging — select:
  - `Backend (Java 21 / Maven)`
  - `Frontend (React / TypeScript)`
  - `Backend images`
  - `Frontend image`
  - `CodeQL (java-kotlin)` and `CodeQL (javascript-typescript)` *(optional — enable once the baseline is clean)*
- ✅ Require branches to be up to date before merging
- ✅ Require conversation resolution
- ✅ Do not allow bypassing the above (or restrict to admins)
- ✅ Restrict direct pushes (no push without PR)

There is no `develop` branch to protect.

---

## 9. CD status

**CI is ready, but no CD deployment target has been configured.**

The repository contains only scaffolding for infrastructure:

- `infrastructure/terraform/` — empty (`.gitkeep` only, modules `vpc`/`rds`/`ec2` unimplemented)
- `infrastructure/k8s/` — empty (`.gitkeep` only)
- the old `.gitlab-ci.yml` deployed over SSH to `$QA_HOST` / `$UAT_HOST` /
  `$PROD_HOST` + `docker-compose pull && up -d`, but those hosts/credentials
  do not exist in this repo.

No AWS / ECS / EKS / App Runner / Render / Railway / Fly config is present, so a
deployment target has **not been invented**. Once a target is chosen, add
`cd-staging.yml` / `cd-production.yml` that:

1. trigger on `push` to `main` (staging) and `workflow_dispatch` /
   release tag (production);
2. require `ci.yml` + `docker.yml` + `security.yml` success;
3. deploy the **`<git-sha>`** image tags (never `main`/`latest`);
4. run smoke tests against `/actuator/health` of each service;
5. use GitHub Environment `production` with required reviewers.

---

## 10. Rollback

Because every image is published with an **immutable `<git-sha>` tag**, rollback
is a re-point, not a rebuild.

```bash
# 1. Find the last-good commit sha (GHCR "Packages" tab, or):
git log --first-parent --oneline main

# 2. On the deploy host, pin every service to that sha and restart:
export TAG=<previous-good-git-sha>
cd /opt/farm2home
sed -i "s|farm2home/\([a-z-]*\):.*|farm2home/\1:${TAG}|" docker-compose.deploy.yml
docker compose -f docker-compose.deploy.yml pull
docker compose -f docker-compose.deploy.yml up -d

# 3. Verify
for p in 8081 8082 8083 8084 8085 8086 8087 8094; do
  curl -fsS localhost:$p/actuator/health || echo "port $p UNHEALTHY"
done
```

**Rollback golden rules**

- Never rebuild an old commit — deploy the already-published old image.
- Keep at least the last 5 `<git-sha>` tags in GHCR (don't prune aggressively).
- Roll **all** services back together unless you know an inter-service contract
  is unchanged.

### Database migrations & rollback

Migrations are **Flyway, forward-only** (`spring.flyway.enabled=true`,
`baseline-on-migrate=true`, per-service `db/migration/` folders,
`ddl-auto=validate`). Flyway Community has **no `undo`**.

- Rolling application code back to `<git-sha>` does **not** revert schema
  changes. An old jar runs fine against a newer schema **only** if the newer
  migrations were additive (new nullable columns / new tables).
- **Therefore: every migration must be backward-compatible** — add columns as
  nullable or with defaults, never rename/drop in the same release that stops
  using them (use expand → migrate → contract across two releases).
- A destructive migration that must be undone requires a **new forward
  migration** (`V<n+1>__revert_xxx.sql`) or a point-in-time DB restore. Take a
  snapshot before any release containing a `DROP` / `ALTER ... TYPE` / data
  backfill.
- CI validates that migrations *apply cleanly* (integration tests run Flyway on
  a fresh Testcontainers DB) but does **not** run migrations against any real
  database — that only happens when a service boots in its target environment.

---

## 11. Troubleshooting

| Symptom | Cause / fix |
|---|---|
| Backend job: `Could not find a valid Docker environment` | Only happens locally without Docker. On `ubuntu-latest` Docker is present; if it recurs, the runner image changed — add `docker info` as a debug step. |
| Backend job fails only on `jacoco:check` / `Coverage ... < 0.80` | A module is below the 80 % line-coverage gate in `backend/pom.xml`. Add tests. Do **not** lower the threshold or skip. |
| Backend IT flakiness / Testcontainers pull timeouts | Transient registry issue — re-run. Consider `testcontainers.reuse.enable` is **not** set (containers are fresh per class by design). |
| `mvn` OOM on the runner | Add `MAVEN_OPTS: -Xmx3g` to the job `env:`. |
| Frontend `ESLint couldn't find a configuration file` | `frontend/.eslintrc.cjs` missing/not committed. |
| Frontend `No test files found, exiting with code 1` | `--passWithNoTests` missing from the `test` script (see §4). |
| `npm ci` fails: lockfile out of sync | Run `npm install` locally, commit `package-lock.json`. |
| Docker job: `denied: permission_denied` pushing to GHCR | First push of a package is private & owner-scoped; ensure the workflow has `permissions: packages: write` (it does) and the actor can write packages. |
| Docker backend build: `COPY target/*.jar: no source files` | The `mvn package` step didn't run or failed for that module — check the Package step log. |
| CodeQL Java job slow/failing | It runs a full `mvn package`; same failures as the backend job. Fix the build first. |

---

## 12. Command reference (CI ⇄ local parity)

```bash
# ---- Backend ----
cd backend
mvn -B clean verify                     # everything CI runs (needs Docker for ITs)
mvn -B -DskipTests clean package        # just the jars (what docker.yml runs)
mvn -B -pl customer-service -am test    # one service's unit tests, fast

# ---- Frontend ----
cd frontend
npm ci
npm run lint
npx tsc --noEmit
npm test
npm run build

# ---- Images ----
cd backend && mvn -B -DskipTests clean package && cd ..
docker build -t farm2home/<service>:dev backend/<service>
docker build -t farm2home/frontend:dev  frontend
```
