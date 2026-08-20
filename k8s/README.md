# K3s test deployment

This directory contains the version-controlled Kubernetes baseline for the Homelab Defender test deployment.

The manifest deliberately does **not** contain registry credentials. `k3s-node-01` obtains private-registry endpoint and authentication settings from `/etc/rancher/k3s/registries.yaml`.

The baseline YAML defines the Namespace, Deployment structure, ClusterIP Service and health probes. The live application image tag is selected by the Jenkins deployment stage using the Jenkins build number.

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

## Baseline apply

Use the manifest to create or reconcile the Kubernetes object structure:

```bash
sudo k3s kubectl apply -f k8s/homelab-defender-test.yaml
sudo k3s kubectl -n homelab-defender-test \
  rollout status deployment/homelab-defender --timeout=120s
```

The image tag contained in the YAML is a bootstrap/baseline value. After automated delivery is enabled, normal releases are performed by Jenkins rather than by manually editing and applying the image tag in this file.

Because `kubectl apply` reconciles all fields in the manifest, manually reapplying an older baseline can change the live image back to the image recorded in the YAML. Check the current running release before using the baseline as a recovery action.

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

Git is the source of truth for the deployment **structure and automation**:

- Namespace, Deployment structure, Service and probes: `k8s/homelab-defender-test.yaml`
- Jenkins delivery logic: `Jenkinsfile`
- Restricted deployment implementation: `ops/deploy-homelab-defender`

The live release number is supplied by Jenkins at deployment time and is visible in the Deployment image field and the `kubernetes.io/change-cause` annotation. This avoids committing a new manifest change purely to record every Jenkins build number while still keeping the release traceable.
