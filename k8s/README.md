# K3s test deployment

This directory documents the Homelab Defender build and controlled deployment workflow.

The authoritative Namespace, Deployment, Service, health probes and approved image digest are maintained in `jrwroberts1976/kubernetes-homelab/applications/homelab-defender-test`. Registry credentials are not stored in either repository; `k3s-node-01` obtains its private-registry configuration from `/etc/rancher/k3s/registries.yaml`.

## First-time checkout on k3s-node-01

If this repository has not yet been cloned on `k3s-node-01`:

```bash
mkdir -p ~/projects
cd ~/projects
git clone https://github.com/jrwroberts1976/jenkins-gradle-delivery-lab.git
cd jenkins-gradle-delivery-lab
```

For later updates:

```bash
cd ~/projects/jenkins-gradle-delivery-lab
git pull --ff-only
```

## Authoritative Kubernetes desired state

The Namespace, Deployment, Service, health probes and approved image identity are maintained in:

```text
jrwroberts1976/kubernetes-homelab/applications/homelab-defender-test
```

Current approved release:

```text
Jenkins build 15
192.168.2.220:5000/homelab-defender:15@sha256:2154a1881acc63db852dbeebc7daf5890a1c9527c4b70837b2ad33fb76ad940b
```

This was reconciled through `kubernetes-homelab#11`, merge commit:

```text
1565663aa0ed1584a09bdc0761ce5e143bf61cce
```

Validate and apply the Kustomization from the Kubernetes repository:

```bash
cd ~/projects/kubernetes-homelab
sudo k3s kubectl kustomize applications/homelab-defender-test \
  >/var/tmp/homelab-defender-managed.yaml
sudo k3s kubectl apply --dry-run=server \
  --filename /var/tmp/homelab-defender-managed.yaml
sudo k3s kubectl apply \
  --filename /var/tmp/homelab-defender-managed.yaml
sudo k3s kubectl -n homelab-defender-test \
  rollout status deployment/homelab-defender --timeout=120s
```

Do not restore the retired manifest from this repository. Before applying desired state, confirm the approved tag and digest in `kubernetes-homelab` represent the intended release.

## Automated release

When a Jenkins build is started with:

```text
PUBLISH_CONTAINER=true
```

the pipeline performs:

```text
Test
→ Package
→ Containerise
→ Trivy Security Scan
→ Publish image
→ Deploy to K3s
→ Service-level health verification
```

Jenkins publishes:

```text
192.168.2.220:5000/homelab-defender:<BUILD_NUMBER>
```

and connects to `k3s-node-01` using the dedicated `jenkins-deploy` SSH credential.

The SSH key is constrained by a forced command, so it cannot be used as a normal shell. The deployment request is limited to:

```text
deploy <BUILD_NUMBER>
```

The root-owned implementation is:

```text
/usr/local/sbin/deploy-homelab-defender
```

with version-controlled source at:

```text
ops/deploy-homelab-defender
```

The helper updates only the Homelab Defender Deployment, waits for the rollout, then checks the application through the private ClusterIP Service.

## Release history

### Build 12 — first gated private-registry publication

Build 12 proved the Trivy-gated registry publication path and authenticated K3s/containerd pull.

### Build 13 — automatic rollback proof

Build 13 rolled out successfully but the first one-shot Service health check received a transient connection reset. The helper restored build 12 and Jenkins marked the release failed. Health verification was then improved to retry `/healthz` up to 15 times before declaring failure.

### Build 14 — first successful end-to-end automated release

Jenkins build 14 completed the full automated path successfully and deployed:

```text
192.168.2.220:5000/homelab-defender:14
```

The final verification reported:

```text
Health check passed on attempt 1/15.
Deployment of 192.168.2.220:5000/homelab-defender:14 completed successfully.
```

### Build 15 — current validated release

Build 15 is the current approved release and completed the full path while the dedicated Defender monitoring was live.

Jenkins result:

```text
SUCCESS
```

Validated runtime state:

```text
Namespace:  homelab-defender-test
Deployment: homelab-defender
Ready:      1/1
Available:  1
Pod state:  Running
Restarts:   0
Service:    homelab-defender
Port:       8080/TCP
```

Running image digest:

```text
sha256:2154a1881acc63db852dbeebc7daf5890a1c9527c4b70837b2ad33fb76ad940b
```

The same immutable identity is now recorded in the Git-owned Deployment manifest.

## Verify the live release

Check which image Kubernetes is currently configured to run:

```bash
sudo k3s kubectl -n homelab-defender-test \
  get deployment homelab-defender \
  -o jsonpath='{.spec.template.spec.containers[0].image}{"\n"}'
```

Inspect pods and the Service:

```bash
sudo k3s kubectl -n homelab-defender-test get pods -o wide
sudo k3s kubectl -n homelab-defender-test get svc
```

Check the running image identity:

```bash
sudo k3s kubectl -n homelab-defender-test \
  get pod \
  -l app=homelab-defender \
  -o jsonpath='{range .items[*]}{.metadata.name}{"\t"}{.status.containerStatuses[0].imageID}{"\n"}{end}'
```

For containerd inventory and digest verification:

```bash
sudo k3s crictl images --digests | grep homelab-defender
```

The registry digest and containerd local image/config ID are different identifiers. For build 15 the approved registry digest is:

```text
sha256:2154a1881acc63db852dbeebc7daf5890a1c9527c4b70837b2ad33fb76ad940b
```

Check the application health endpoint through the ClusterIP Service:

```bash
SVC_IP=$(
  sudo k3s kubectl -n homelab-defender-test \
    get svc homelab-defender \
    -o jsonpath='{.spec.clusterIP}'
)

curl -i "http://${SVC_IP}:8080/healthz"
```

Expected response:

```text
HTTP/1.1 200 OK

ok
```

Check the application page:

```bash
curl -s "http://${SVC_IP}:8080/" | head
```

## Monitoring validation

The current release is observed through:

```text
Homelab Defender on k3s-node-01
        ↓
kube-state-metrics 192.168.2.211:8080
        ↓
Prometheus on ids-01
        ↓
Grafana on ids-01
```

Build 15 validation confirmed:

```text
Homelab Defender Deployment Unavailable -> 0
Homelab Defender New Container Restart  -> 0
```

The build-15 pod had zero restarts at validation time.

Dedicated Grafana objects:

```text
Dashboard UID: homelab-defender-k8s
Deployment unavailable alert UID: ffwbnisgmg4cgb
New restart alert UID: afwbnisiruz28f
```

## Trivy cache note

Build 15 took longer because Trivy refreshed both its vulnerability database and Java database. The initial vulnerability-DB mirror returned `BLOB_UNKNOWN`; Trivy automatically used its fallback repository and completed successfully.

The persistent `trivy-cache` volume was verified populated after the release, so this was a legitimate database refresh rather than a cache failure. No Jenkinsfile change is required from that observation.

## Automatic rollback

The Jenkins deployment path remembers the image that was running before an update.

If the rollout fails, or the Service-level `/healthz` check never succeeds, the deployment helper attempts to restore that previous image and waits for the rollback rollout to complete.

Build 13 proved this behaviour. Health verification now retries up to 15 times before declaring failure, which avoids treating a brief Service datapath convergence delay as a bad application release.

## Manual rollback

Inspect deployment revisions:

```bash
sudo k3s kubectl -n homelab-defender-test \
  rollout history deployment/homelab-defender
```

Roll back to the previous Kubernetes revision:

```bash
sudo k3s kubectl -n homelab-defender-test \
  rollout undo deployment/homelab-defender

sudo k3s kubectl -n homelab-defender-test \
  rollout status deployment/homelab-defender --timeout=120s
```

A previous revision only exists after at least one later rollout has replaced the current Deployment template.

## Source of truth and release state

Desired-state ownership is split deliberately:

- Namespace, Deployment, Service, probes and approved image digest: `jrwroberts1976/kubernetes-homelab/applications/homelab-defender-test`
- Application source, tests and Jenkins delivery logic: this repository and `Jenkinsfile`
- Restricted node-side deployment implementation: `ops/deploy-homelab-defender`
- Operational release evidence: `jrwroberts1976/home-lab-docs/jenkins`
- Grafana dashboard/rule source: `jrwroberts1976/grafana-alerting`

A successful Jenkins deployment can temporarily advance the live image ahead of the desired-state manifest. Reconcile each approved tag and digest into `kubernetes-homelab` after release validation, and never apply an older manifest over a newer healthy deployment.
