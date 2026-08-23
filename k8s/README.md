# K3s test deployment

This directory documents the Homelab Defender build and controlled deployment workflow.

The authoritative Namespace, Deployment, Service, health probes and approved image digest are maintained in `jrwroberts1976/kubernetes-homelab/applications/homelab-defender-test`. Registry credentials are not stored in either repository; `k3s-node-01` obtains its private-registry configuration from `/etc/rancher/k3s/registries.yaml`.

## First-time checkout on k3s-node-01

If this repository has not yet been cloned on `k3s-node-01`, create the projects directory and clone the public repository:

```bash
mkdir -p ~/projects
cd ~/projects
git clone https://github.com/jrwroberts1976/jenkins-gradle-delivery-lab.git
cd jenkins-gradle-delivery-lab
```

For later updates, use:

```bash
cd ~/projects/jenkins-gradle-delivery-lab
git pull
```

## Authoritative Kubernetes desired state

The Namespace, Deployment, Service, health probes and approved image digest are maintained in:

`jrwroberts1976/kubernetes-homelab/applications/homelab-defender-test`

Validate and apply that Kustomization from the Kubernetes repository:

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

Do not restore the retired manifest from this repository. Confirm that the approved tag and digest in `kubernetes-homelab` match the intended release before applying it.

## Automated release

When a Jenkins build is started with:

```text
PUBLISH_CONTAINER=true
```

the current pipeline performs:

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

and then connects to `k3s-node-01` using the dedicated `jenkins-deploy` SSH credential.

The SSH key is constrained by a forced command, so it cannot be used as a normal shell. The deployment request is limited to:

```text
deploy <BUILD_NUMBER>
```

The root-owned implementation is:

```text
/usr/local/sbin/deploy-homelab-defender
```

with the version-controlled source stored at:

```text
ops/deploy-homelab-defender
```

The script updates only the Homelab Defender Deployment, waits for the rollout, then checks the application through the private ClusterIP Service.

## First successful end-to-end release

Jenkins build 14 completed the complete automated path successfully and deployed:

```text
192.168.2.220:5000/homelab-defender:14
```

The final deployment verification reported:

```text
Health check passed on attempt 1/15.
Deployment of 192.168.2.220:5000/homelab-defender:14 completed successfully.
```

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

## Automatic rollback

The Jenkins deployment path remembers the image that was running before an update.

If the rollout fails, or the Service-level `/healthz` check never succeeds, the deployment script attempts to restore that previous image and waits for the rollback rollout to complete.

Build 13 proved this behaviour. The new image rolled out, but the first one-shot Service health check received a transient connection reset. The script automatically restored build 12 and Jenkins marked the release as failed.

Health verification now retries up to 15 times before declaring failure, which avoids treating a brief Service datapath convergence delay as a bad application release.

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
- Application source, tests and Jenkins delivery logic: `Jenkinsfile`
- Restricted node-side deployment implementation: `ops/deploy-homelab-defender`

The Jenkins deployment path uses the restricted `jenkins-deploy` SSH account and accepts only `deploy BUILD_NUMBER`. During the transition to Git-driven release updates, a successful Jenkins deployment can advance the live image ahead of the desired-state manifest. Reconcile the newly approved tag and digest into `kubernetes-homelab` after each release, and never apply an older manifest over a newer healthy deployment.
