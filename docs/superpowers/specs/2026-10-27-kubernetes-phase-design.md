# Phase 2: Kubernetes

Roadmap: CI (done) -> **Kubernetes** -> GitOps CD (Argo CD) -> extras (Sealed Secrets, observability, IaC).
Branch for Part A: `k8s-readiness` (from `develop`). Part B lives in a new repo, `homefinance-gitops`.

## Goal
Run the home finance app on a local multi-node Kubernetes cluster using a hand-written Helm chart, with two environments (dev, prod-like), and practice the operational concepts: rolling updates, probes, resource limits, RBAC, network policies, ingress, persistent storage, node failure. Deploys are manual (`helm upgrade --install`) in this phase; Phase 3 hands them to Argo CD.

## Part A: app-repo readiness (`homefinance-app`, branch `k8s-readiness`)

The images must work behind any hostname and expose health endpoints.

1. **Frontend API URL.** In `frontend/src/services/api.ts` replace the hardcoded `http://localhost:8585/api/v1` with the relative `/api/v1`. Add a Vite dev-server proxy (`/api` -> `http://localhost:8585`) so `npm run dev` still works.
2. **Nginx proxy.** In `frontend/nginx.conf` add `location /api/ { proxy_pass http://backend:8585; ... }` with `client_max_body_size` raised for CSV uploads and sensible proxy timeouts. The Service is named `backend` in both docker-compose and Kubernetes, so the same image works in both and CORS is no longer involved. `docker-compose.yml` keeps serving the UI on port 3001.
3. **Health endpoints.** Add `spring-boot-starter-actuator`. Expose only `health`, with liveness and readiness probe groups enabled (`management.endpoint.health.probes.enabled=true`, `management.endpoints.web.exposure.include=health`). Liveness must not depend on the database; readiness may.
4. **Cleanup.** Remove the unused `spring.data.mongodb.*` lines from `application.properties` (no MongoDB code or dependency exists; the URI carried a live credential in a public repo).
5. **Compose smoke test.** `docker compose up --build` must still work end to end, including data loading through the proxy.

Out of scope: auth, TLS, changing CORS config (left in place, now unused in the proxied setup).

## Part B: `homefinance-gitops` repo and cluster

### Repo layout
```
homefinance-gitops/
  charts/homefinance/        # own Helm chart (templates, values.yaml)
  envs/dev/values.yaml       # image tags, replicas, hostnames, resources for dev
  envs/prod/values.yaml      # same for prod-like
  cluster/                   # cluster-level setup: minikube profile, ingress-nginx values, bootstrap scripts
  docs/                      # runbook and exercises
```
Public repo; contains no secrets. `main` is protected the same way as the app repo (PRs only), with a lightweight `helm lint` + `helm template` check as its CI.

### Cluster
- minikube profile `homefinance`: `--nodes 3 --cni=calico --driver=docker`, about 2.5 GB RAM and 2 CPUs per node. Calico because minikube's default CNI does not enforce NetworkPolicies.
- Namespaces: `homefinance-dev`, `homefinance-prod`, `ingress-nginx`.
- ingress-nginx installed from its community Helm chart with values kept in `cluster/`.
- Access via `minikube tunnel` plus hosts-file entries `dev.homefinance.local` and `prod.homefinance.local` pointing at 127.0.0.1.

### Chart `charts/homefinance`
| Component | Resources |
|---|---|
| backend | Deployment (2 replicas in prod, 1 in dev), Service `backend:8585`, ConfigMap (profile, datasource URL), env from an existing Secret |
| frontend | Deployment, Service `frontend:80`, Nginx proxies `/api/` to `backend` |
| mysql | StatefulSet (1 replica), headless Service, PVC (2Gi), env from an existing Secret |
| ingress | Ingress to `frontend` for the environment's hostname |
| security | ServiceAccount per component with `automountServiceAccountToken: false`; securityContext (non-root, drop capabilities) where the images allow it |
| network | NetworkPolicies: default deny; MySQL accepts only backend; backend accepts only frontend; frontend accepts only ingress-nginx; DNS egress allowed |

Common settings: resource requests and limits on every container; `startupProbe` (Spring Boot starts slowly), `readinessProbe` and `livenessProbe` on backend (`/actuator/health/readiness`, `/actuator/health/liveness`) and frontend (`/`); `RollingUpdate` with `maxUnavailable: 0, maxSurge: 1`; standard `app.kubernetes.io/*` labels.

### Secrets
The chart never creates the DB credentials. It references a Secret by name (`existingSecret` value). `cluster/bootstrap.ps1` creates the Secret in each namespace from a gitignored `.env` file. This keeps the public repo credential-free and sets up Sealed Secrets as the later replacement.

### Images
Pulled from `ghcr.io/0x4147/homefinance-{backend,frontend}:<tag>`. The two GHCR packages are made public (source is public). Tags are the `sha-xxxxxxx` values published by the app repo's `publish.yml`; the tag lives in `envs/<env>/values.yaml`.

### Deploy (this phase)
`helm upgrade --install homefinance charts/homefinance -n homefinance-<env> -f envs/<env>/values.yaml`. Image tag bumps are manual edits to `values.yaml`, committed by PR.

## Exercises (the learning outcomes)
1. Zero-downtime rolling update: bump the tag while a `curl` loop hits the ingress; no failed requests.
2. Kill a backend pod: it is replaced, traffic continues.
3. Stop a node (`minikube node stop`): pods reschedule; observe that MySQL is pinned to its node's volume and does not move (storage lesson).
4. NetworkPolicy proof: from a throwaway pod, connecting to MySQL is blocked; from the backend it works.
5. Resource limits: observe a limit being applied (`kubectl top` / describe), and what an OOM kill looks like.
6. Dev and prod run side by side with different replicas and hostnames from the same chart.

## Verification
- Part A: `docker compose up --build`; the dashboard loads data via `http://localhost:3001` with the browser talking only to port 3001; `/actuator/health` returns UP from the backend; all four CI checks pass on the PR.
- Part B: `kubectl get nodes` shows 3 Ready nodes; `helm lint` and `helm template` are clean; both environments serve the app through the ingress and the dashboard shows data; all six exercises succeed.

## Risks / notes
- RAM: 3 nodes at about 2.5 GB plus existing Docker containers; reduce node memory or count if the machine strains.
- `minikube tunnel` needs an elevated terminal on Windows and must stay running.
- Calico adds memory overhead and startup time; fallback is no CNI enforcement (policies become documentation only).
- Flyway runs on backend startup; with 2 prod replicas it uses its schema lock table, so concurrent start is safe.
- The backend image still carries 40 known CVEs until the Spring Boot upgrade lands (separate branch).
- The previous MongoDB credential must be rotated in Atlas by the owner; removing it from the file does not remove it from git history.
