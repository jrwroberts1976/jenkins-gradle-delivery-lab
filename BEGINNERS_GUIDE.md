# Homelab Defender — Beginner’s Guide

This project is a safe place to learn how a small application moves from an idea in GitHub to a running, monitored service.

The application is **Homelab Defender**, a small browser game based on familiar operational problems such as failed backups, low disk space and security updates. The game matters, but the main goal is learning the delivery process behind it.

For terminology, see the [Glossary](GLOSSARY.md).

## The simple picture

```text
Write code
  → test it
  → package it
  → build a container
  → security-check the container
  → store it in a private registry
  → deploy it automatically to Kubernetes
  → prove it is healthy
  → monitor it
  → record the approved release in Git
  → roll back automatically if verification fails
```

Each step gives us evidence that the next step is safe to attempt.

## The main parts

| Name | Plain-English meaning | Where it runs |
|---|---|---|
| GitHub | The shared project folder and change history | Internet |
| Java | The language used to write the game | Source code |
| Gradle | The repeatable build recipe | Jenkins and developer workstation |
| Jenkins | The automated build and delivery worker | TestServer, internal only |
| Docker | A standard package containing the app and what it needs to run | Jenkins builder |
| Trivy | A security inspector that checks the Docker image | Jenkins delivery process |
| Private registry | A protected storage shelf for approved images | TestServer LAN |
| containerd | The runtime K3s uses to pull and run images | `k3s-node-01` |
| K3s / Kubernetes | The platform that runs the packaged game | `k3s-node-01` |
| kube-state-metrics | Exposes Kubernetes object state as metrics | K3s monitoring namespace |
| Prometheus | Stores monitoring metrics | `ids-01` |
| Grafana | Shows the Defender dashboard and evaluates alerts | `ids-01` |
| `kubernetes-homelab` | The Git source of truth for approved Kubernetes state | GitHub |
| Cloudflare | The planned future public front door for the game | Internet edge |

## What happens when we publish a build

The full release path runs when `PUBLISH_CONTAINER=true` is selected:

1. Jenkins checks out the exact GitHub commit.
2. Jenkins runs the Gradle tests.
3. Jenkins packages the application.
4. Jenkins builds a Docker image tagged with the Jenkins build number.
5. Trivy scans the image for HIGH and CRITICAL vulnerabilities.
6. If the security gate passes, Jenkins authenticates to the private registry and pushes the image.
7. Jenkins opens a restricted SSH connection to `k3s-node-01` and requests only `deploy <BUILD_NUMBER>`.
8. K3s/containerd authenticates to the registry and pulls the requested image.
9. Kubernetes performs a rolling update in the isolated test namespace.
10. The deployment helper waits for the rollout, then checks `/healthz` through the ClusterIP Service.
11. If verification fails persistently, the helper attempts to restore the image that was running before the release.
12. Prometheus and Grafana observe the new deployment and pod state.
13. After validation, the approved tag and immutable digest are reconciled into `kubernetes-homelab`.

That gives us a chain of evidence from source commit to a healthy, monitored and Git-recorded Kubernetes workload.

## The security gate

An early image, `homelab-defender:6`, contained 8 HIGH findings in `usr/bin/pebble`. Trivy was configured with `--exit-code 1`, so those findings demonstrated that policy-breaking images can stop the pipeline before publication.

The runtime was changed to:

```text
eclipse-temurin:21-jre-jammy
```

Compliant images then returned 0 HIGH/CRITICAL findings under the project policy.

The important rule is:

```text
Vulnerable image
  → Trivy fails
  → pipeline stops
  → image is not published

Compliant image
  → Trivy passes
  → pipeline may continue
  → image can be published and deployed
```

## Why the registry has separate accounts

The private registry uses HTTP Basic authentication backed by an `htpasswd` file.

Jenkins uses the dedicated `jenkins-ci` account for publishing. K3s uses a different account for runtime pulls. This avoids reusing the more powerful publishing credential inside the runtime platform.

The registry is also protected by the TestServer firewall. TCP port 5000 is limited to the systems and networks that need it.

## How K3s reaches the registry

K3s uses containerd as its container runtime. Private-registry settings live in:

```text
/etc/rancher/k3s/registries.yaml
```

The configuration tells K3s to use the internal HTTP registry and the dedicated pull credentials.

During the first test, K3s had not yet reloaded this file and containerd tried HTTPS, which failed. After restarting K3s, containerd used the intended HTTP endpoint and authenticated pull path successfully.

## The Kubernetes test environment

Homelab Defender runs in the isolated namespace:

```text
homelab-defender-test
```

A Kubernetes `Deployment` manages one application pod. A private `ClusterIP` Service exposes it inside the cluster on port `8080`.

The application exposes:

```text
/healthz
```

and a healthy response is:

```text
ok
```

The endpoint is used for readiness, liveness and the post-rollout service-level health check.

## Why Jenkins does not get full Kubernetes access

Jenkins needs to release this application, but it does not need unrestricted cluster administration.

A dedicated account named `jenkins-deploy` is used for SSH. Its key is constrained by a forced command, so it cannot open a normal shell.

The only accepted request is effectively:

```text
deploy <BUILD_NUMBER>
```

The request reaches a root-owned deployment helper through a narrow sudo rule. The helper validates the build number and only operates on the Homelab Defender deployment.

## Release history

### Build 12 — first gated publication

Build 12 completed the first successful security-gated publication to the private registry and proved that K3s/containerd could authenticate and pull the private image.

### Build 13 — rollback proof

Build 13 was the first automated deployment attempt. Build, scan, publish and rollout succeeded, but the first Service-level `/healthz` request received a transient connection reset. The deployment helper restored build 12 and Jenkins marked build 13 failed.

That failure proved the automatic rollback path. The health check was then improved to retry up to 15 times before declaring a release unhealthy.

### Build 14 — first fully automated end-to-end release

Build 14 completed the entire source-to-healthy-Kubernetes chain without a manual deployment step.

It passed tests, packaging and Trivy, published:

```text
192.168.2.220:5000/homelab-defender:14
```

and completed the restricted deployment with `/healthz` passing on attempt `1/15`.

### Build 15 — current validated release

Build 15 is the current approved release and the first release validated while the dedicated Defender dashboard and alert rules were live.

Approved immutable identity:

```text
192.168.2.220:5000/homelab-defender:15@sha256:2154a1881acc63db852dbeebc7daf5890a1c9527c4b70837b2ad33fb76ad940b
```

Validation showed:

- Jenkins result `SUCCESS`;
- K3s Deployment `1/1` available;
- new pod `Running` with zero restarts;
- Prometheus saw the build-15 image identity;
- both Defender alert expressions were `0`;
- the tag and digest were reconciled into `kubernetes-homelab` through pull request `#11`.

This means the release was not only built and deployed successfully; it was also observed by the monitoring stack and recorded immutably in Git.

## Why build 15 took longer

During build 15, Trivy refreshed both its normal vulnerability database and its Java database.

The first vulnerability database mirror returned `BLOB_UNKNOWN`, so Trivy automatically used its fallback registry and continued successfully. The Java database was also downloaded.

The persistent `trivy-cache` volume was checked afterwards and contained about `2.6G` of cached data. This proved the long scan was a legitimate database refresh rather than a broken cache.

## What the Jenkins build choices mean

| Option | What it does |
|---|---|
| `BUILD_CONTAINER` | Runs tests, packages the game, builds an image and runs the Trivy gate. It does not publish or deploy. |
| `PUBLISH_CONTAINER` | Runs the complete release path: build, scan, publish, deploy, rollout wait and health verification. |

Publishing implies the image must first be built and pass the security gate, so `PUBLISH_CONTAINER=true` is enough for a full release.

Each release uses the Jenkins build number as its image tag, for example:

```text
homelab-defender:15
```

This is safer than an ambiguous moving `latest` tag because the release can be traced back to one Jenkins run.

## Git and the live release version

The authoritative Kubernetes files live in:

```text
jrwroberts1976/kubernetes-homelab/applications/homelab-defender-test
```

They define the Namespace, Deployment, Service, health probes and approved image tag plus digest.

Jenkins can temporarily advance the running image during a gated release. After validation, the approved tag and digest are reconciled into Git so `kubernetes-homelab` remains authoritative.

Do not apply an older desired-state image over a newer healthy Jenkins deployment. Reconcile Git first.

## Monitoring the release

The operational path is:

```text
Homelab Defender on k3s-node-01
        ↓
kube-state-metrics
        ↓
Prometheus on ids-01
        ↓
Grafana on ids-01
```

The dedicated dashboard is:

```text
Homelab Defender Kubernetes Operations
```

The dedicated alert rules detect:

- the Defender Deployment becoming unavailable;
- a new Defender container restart.

Build 15 rolled out with both alert expressions remaining zero.

## What happens next

The delivery and monitoring mechanisms are now end to end. The next improvements are:

1. decide how to expose the game through a controlled Cloudflare route;
2. replace Docker's deprecated legacy builder with BuildKit/buildx;
3. complete Jenkins controller/data recovery documentation and bring remaining host-specific Jenkins runtime definitions under controlled source ownership.

## Safety boundaries

- Jenkins remains internal-only.
- Jenkins does not receive TestServer's host Docker socket.
- Jenkins-to-builder Docker traffic uses TLS.
- Trivy is temporary and version-pinned.
- A failed security gate prevents publication.
- Registry credentials stay outside Git.
- K3s uses a dedicated registry pull credential.
- The registry is firewall-restricted.
- Jenkins deployment uses a dedicated restricted SSH identity rather than general cluster-admin access.
- The Kubernetes workload is isolated in `homelab-defender-test`.
- The Service remains private `ClusterIP`.
- A failed rollout or persistent health-check failure triggers a rollback attempt.
- Releases use explicit Jenkins build-number tags rather than `latest`.
- Approved Kubernetes release state is recorded with an immutable digest in Git.

## Useful mental model

Think of it as a small workshop:

- **GitHub** is the design folder.
- **Gradle** is the build instruction sheet.
- **Jenkins** is the workbench and delivery operator.
- **Docker** is the sealed delivery box.
- **Trivy** is the security inspector.
- **The registry** is the locked stockroom.
- **containerd** is the warehouse handler that fetches the approved box.
- **Kubernetes** puts the box into service and checks it stays healthy.
- **Prometheus and Grafana** are the control-room instruments watching the service.
- **The rollback helper** puts the previous known-good box back if the new one cannot be verified.
- **`kubernetes-homelab`** is the signed release ledger recording what should be running.
- **Cloudflare** will eventually be the public reception desk.
