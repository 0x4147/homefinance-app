# Phase 3: GitOps CD with Argo CD

Roadmap: CI (done) -> Kubernetes (done) -> **GitOps CD** -> extras (Sealed Secrets, observability, IaC).
Touches two repos: `homefinance-app` (CI bump job) and `homefinance-gitops` (Argo manifests, promote workflow).

## Goal
Replace manual `helm upgrade` with a pull-based flow: a merge to `develop` produces an image, a bot PR bumps the tag in the gitops repo, and Argo CD in the cluster deploys whatever is on gitops `main`. Prod is promoted deliberately. Git history becomes the audit trail and `git revert` becomes the rollback.

```
app repo: merge to develop
   -> publish.yml pushes ghcr.io/0x4147/homefinance-*:sha-abc1234
   -> bump job opens PR in gitops repo: envs/dev/values.yaml tags -> sha-abc1234
   -> gitops CI (helm lint/template) passes -> PR auto-merges
   -> Argo CD (in cluster) sees main change -> syncs homefinance-dev automatically

prod: "Promote" workflow (manual, input = verified tag) opens PR for envs/prod/values.yaml
   -> reviewed and merged by a human -> Argo CD shows homefinance-prod OutOfSync -> human clicks Sync
```

## Decisions (confirm or change)
1. **Argo CD installed from the official Helm chart** (`argo/argo-cd`, version pinned at install time), values kept in `homefinance-gitops/cluster/argocd-values.yaml`. Dex, notifications and the ApplicationSet controller are disabled to save RAM; the UI is reached with `kubectl port-forward`, no ingress.
2. **Applications live in the gitops repo** (`argocd/dev.yaml`, `argocd/prod.yaml`) and are applied once by hand (`kubectl apply`). An app-of-apps / ApplicationSet is a later refinement.
3. **Sync policy:** dev is automated (`prune: true`, `selfHeal: true`); prod is manual sync, so promotion has a visible approval step.
4. **Cross-repo write access: fine-grained personal access token** (not `GITHUB_TOKEN`) stored as the secret `GITOPS_PAT` in both repos. Required because PRs opened with `GITHUB_TOKEN` do not trigger workflows, so the gitops `helm` check would never run on the bump PR. A GitHub App is the enterprise equivalent and a possible later upgrade.
5. **Dev bump PRs auto-merge** once the `helm` check passes; **prod promotion PRs do not** (a human merges).
6. **Resource tracking by annotation**, not label (see Gotchas).
7. **DB credentials are still bootstrapped outside Git** (`cluster/bootstrap-secrets.ps1`); Argo does not manage the `homefinance-db` Secret. Sealed Secrets replaces this in a later phase.

## Part A: cluster side (`homefinance-gitops`)

### Install
`cluster/argocd-up.ps1`: add the Argo helm repo, `helm upgrade --install argocd argo/argo-cd -n argocd --create-namespace -f cluster/argocd-values.yaml --wait`. Values:
- `configs.cm."application.resourceTrackingMethod": annotation`
- `dex.enabled: false`, `notifications.enabled: false`, `applicationSet.enabled: false`
- modest resource requests; single replicas
- `configs.params."server.insecure": true` (plain HTTP behind the port-forward)

Access: `kubectl -n argocd port-forward svc/argocd-server 8080:80`; initial admin password from the `argocd-initial-admin-secret` Secret (rotate, then delete that Secret).

### Applications (`argocd/dev.yaml`, `argocd/prod.yaml`)
- `source`: repo `https://github.com/0x4147/homefinance-gitops` (public, no credentials), `targetRevision: main`, `path: charts/homefinance`, helm `valueFiles: [../../envs/<env>/values.yaml]`, `releaseName: homefinance`
- `destination`: in-cluster, namespace `homefinance-<env>`
- `syncPolicy`: dev `automated {prune, selfHeal}` + `CreateNamespace=true`; prod manual
- `ignoreDifferences`: none expected; add only if a real diff appears

### Adopting the existing Helm releases
Both environments are currently installed by the Helm CLI as release `homefinance`. `releaseName: homefinance` keeps rendered resources identical, so Argo adopts them in place without recreating Deployments (the selector label `app.kubernetes.io/instance` is immutable). The old Helm release records are then removed with `kubectl delete secret -n <ns> -l owner=helm,name=homefinance` (workloads untouched), so there is one owner. Persistent volumes and data are not affected.

### Promote workflow (`.github/workflows/promote.yml`)
`workflow_dispatch` with input `tag` (must match `sha-[0-9a-f]{7}`; validated). Checks that both images exist in GHCR for that tag, rewrites the two tag fields in `envs/prod/values.yaml`, pushes a branch `promote/prod-<tag>` and opens a PR. It authenticates with the same fine-grained PAT, stored as a second secret named `GITOPS_PAT` in the gitops repo, so the push and the PR trigger the required `helm` check (a `GITHUB_TOKEN`-created PR would sit forever waiting for it). The PR is **not** auto-merged.

## Part B: app repo (`homefinance-app`)

Add a `bump-dev` job to `.github/workflows/publish.yml`:
- runs only for pushes to `develop`, after the image publish job succeeds (`needs: publish`)
- checks out `0x4147/homefinance-gitops` with `GITOPS_PAT`
- sets `backend.image.tag` and `frontend.image.tag` in `envs/dev/values.yaml` to `sha-<short sha>` (same tag for both images, as published); no-op if unchanged
- pushes branch `bump/dev-sha-<short>`, opens a PR titled "Deploy sha-<short> to dev" linking the app commit, and enables auto-merge (`gh pr merge --auto --merge`)
- concurrent bumps are serialized with a `concurrency` group so two quick merges do not race

## Prerequisites (one-time, manual or via API)
1. Create the fine-grained PAT: repo access only to `homefinance-gitops`; permissions Contents: read/write, Pull requests: read/write; short expiry (90 days) with a calendar reminder. Add it as secret `GITOPS_PAT` in **both** `homefinance-app` (bump job) and `homefinance-gitops` (promote workflow).
2. In `homefinance-gitops` enable "Allow auto-merge" and "Automatically delete head branches" (both currently off). "Allow GitHub Actions to create pull requests" is not needed because the PAT does the work.
3. Argo CD needs about 1 GB of cluster RAM; nodes are about 2.1-2.5 GB each with the current workloads. If pods are evicted or Pending, raise node memory (`minikube node delete` / re-add, or recreate the profile with more) before continuing.

## Gotchas (designed around)
- **Tracking label clash:** Argo's default label-based tracking writes `app.kubernetes.io/instance`, which the chart already uses as an immutable selector label. Annotation tracking avoids rewriting it.
- **Bot PRs and required checks:** the required `helm` check must run on bump PRs, hence the PAT.
- **Auto-merge needs the repo setting and required checks configured;** `strict` (up-to-date branch) is on, so two bump PRs in a row can need a rebase. The `concurrency` group and `update-branch` retry handle the common case.
- **Argo and Helm hooks/tests:** the chart has none today.
- **Self-heal fights manual changes:** during Phase 2 style experiments (scale, cordon, edit), expect Argo to revert dev; use prod or pause auto-sync (`argocd app set homefinance-dev --sync-policy none`).

## Verification
1. `kubectl -n argocd get pods` all Running; UI reachable; both Applications appear **Synced / Healthy** after adoption with no pod restarts and no data change (dev still shows 2,437 transactions).
2. Merge a trivial app change to `develop`: the bump PR appears, its `helm` check runs, it auto-merges, and `homefinance-dev` rolls to the new tag with no manual step (watch with a curl loop: no failed requests).
3. Drift test: `kubectl -n homefinance-dev scale deploy/frontend --replicas=3` is reverted by self-heal within a minute.
4. Prod: run **Promote** with the verified tag; PR opens and passes CI; after merge the prod Application is OutOfSync until Sync is clicked; the rolling update is zero-downtime.
5. Rollback: `git revert` the bump commit on gitops `main`; dev returns to the previous tag without touching the cluster.
6. Failure case: open a gitops PR that breaks the chart; CI blocks it. Merge one that renders but fails at runtime (bad image tag) and watch Argo show Degraded while old pods keep serving (maxUnavailable 0).

## Out of scope
Argo CD SSO/RBAC, notifications, multi-cluster, Image Updater, managing the DB Secret through Git (Sealed Secrets phase), TLS and a real ingress for the Argo UI.
