# Phase 1: CI pipeline and image hardening

Branch: `ci/github-actions` (from `develop`). Overall roadmap: CI -> Kubernetes -> GitOps CD (Argo CD) -> extras.

## Goal
Every PR is automatically tested, built and scanned. Every merge to `develop` or `main` publishes versioned, deployable images to GHCR. This is the foundation for the Kubernetes and GitOps phases, and a way to practice enterprise CI concepts.

## Scope
In: GitHub Actions workflows, branch protection, Dockerfile hardening, a `.dockerignore` for each image.
Out: gitops repo, cluster, Helm, Argo CD, image-tag bumping (Phase 3), frontend runtime API URL fix and Actuator health endpoint (Phase 2, separate PR). No new unit tests (per CLAUDE.md).

## Workflows (`.github/workflows/`)

### `ci.yml` - pull requests
Trigger: `pull_request` targeting `develop` or `main`. `concurrency` cancels superseded runs per PR. Default `permissions: contents: read`.

| Job | Steps |
|---|---|
| `backend` | checkout, `setup-java` 17 (Temurin) with Maven cache, `./mvnw test` in `backend/` |
| `frontend` | checkout, `setup-node` 20 with npm cache, `npm ci --legacy-peer-deps`, `npm run lint`, `npm run build` in `frontend/` |
| `images` | for backend and frontend (matrix): Buildx build with GHA layer cache, `load` (no push), Trivy scan failing on HIGH/CRITICAL with `ignore-unfixed: true` |

Path filters: `backend/**` triggers backend and its image; `frontend/**` triggers frontend and its image; workflow-file changes trigger everything. Required status checks must still report on every PR, so filtering is done inside jobs (a `changes` job using `dorny/paths-filter`) rather than at the workflow trigger level.

### `publish.yml` - merges
Trigger: `push` to `develop` or `main`. Permissions: `contents: read`, `packages: write`. Logs in to GHCR with `GITHUB_TOKEN` (no stored secrets).
Builds and pushes `ghcr.io/0x4147/homefinance-backend` and `ghcr.io/0x4147/homefinance-frontend`.
Tags: short SHA (`sha-abc1234`) on every push; `latest` on `main` only. Branch name is not used as a tag, so a tag always identifies exactly one build. The SHA tag is what Phase 3 will write into the gitops repo.

Third-party actions are pinned to a major version tag (e.g. `actions/checkout@v4`); pinning to commit SHAs is a later hardening step.

## Branch protection (manual, one-time)
On `develop` and `main`: require a pull request, require the `ci.yml` checks (`backend`, `frontend`, `images`) to pass, require branches to be up to date, block force pushes. Exact UI steps will be given at implementation time. Applying the rules to administrators means solo work also goes through PRs, which is the point of the exercise.

## Image hardening

### `backend/Dockerfile`
- Build stage: copy `pom.xml` and run `mvn dependency:go-offline` before copying `src`, so dependencies cache as a layer.
- Runtime stage: `eclipse-temurin:17-jre-alpine` instead of the JDK.
- Run as a non-root user.
- Copy the jar with a wildcard (`target/*.jar` after excluding plain jars) instead of the hardcoded `0.0.1-SNAPSHOT` name.
- Remove the JDWP agent from `ENTRYPOINT`; keep port 5005 out of the image (`EXPOSE 8585` only).
- Local debugging: `docker-compose.yml` sets `JAVA_TOOL_OPTIONS=-agentlib:jdwp=...` on the backend service and keeps the `5005` port mapping, so the dev workflow is unchanged.
- Keep `-DskipTests` in the image build; tests run in the `backend` CI job.

### `frontend/Dockerfile`
- `node:18-alpine` -> `node:20-alpine` (18 is end-of-life).
- No other changes in this PR. Non-root Nginx and the runtime API URL come with the Phase 2 frontend work.

### `.dockerignore`
Add one to `backend/` (`target/`, `.idea/`, `*.log`) and `frontend/` (`node_modules/`, `dist/`), so local build output never enters the image context.

## Verification
1. Locally: `docker compose up --build` still starts the full stack; the app works at `http://localhost:3001`; a debugger attaches to port 5005.
2. Locally: `docker run` the backend image and confirm `whoami` is not root and port 5005 is not listening.
3. Open a PR from `ci/github-actions` to `develop`; all three jobs run and pass. Push a deliberate lint error to confirm the PR is blocked, then revert.
4. After merge to `develop`, `publish.yml` succeeds and both images appear under the repo's Packages with a `sha-` tag; `docker pull` of one works.
5. Branch protection: attempt a direct push to `develop` and confirm it is rejected.

## Risks / notes
- The Trivy gate may fail on base-image CVEs that are out of our control; `ignore-unfixed: true` limits this, and a `.trivyignore` is the escape hatch if needed.
- GHCR packages are private by default; making them public is optional and only matters once the cluster pulls them (Phase 2 will use an image pull secret or make them public).
- The repo has extra remotes (`backend-remote`, `frontend-remote`); workflows live only in `origin` and these are ignored.
