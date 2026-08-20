# K3s test deployment

This directory contains the reproducible Kubernetes definition for the Homelab Defender test deployment.

The manifest deliberately does **not** contain registry credentials. `k3s-node-01` obtains private-registry endpoint and authentication settings from `/etc/rancher/k3s/registries.yaml`.

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

## Deploy

From a checkout of this repository on `k3s-node-01`:

```bash
sudo k3s kubectl apply -f k8s/homelab-defender-test.yaml
sudo k3s kubectl -n homelab-defender-test \
  rollout status deployment/homelab-defender --timeout=120s
```

The current manifest deploys the immutable image:

```text
192.168.2.220:5000/homelab-defender:12
```

For a later release, update the image tag in the manifest to the new Jenkins build number and commit that change before applying it.

## Verify

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

## Rollback

Inspect available deployment revisions:

```bash
sudo k3s kubectl -n homelab-defender-test \
  rollout history deployment/homelab-defender
```

Roll back to the previous successful revision:

```bash
sudo k3s kubectl -n homelab-defender-test \
  rollout undo deployment/homelab-defender

sudo k3s kubectl -n homelab-defender-test \
  rollout status deployment/homelab-defender --timeout=120s
```

A previous revision only exists after at least one later rollout has replaced the current deployment.

After an emergency rollback, update the Git manifest to match the intended running version so Git remains the source of truth.
