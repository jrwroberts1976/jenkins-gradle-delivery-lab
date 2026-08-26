# Homelab Defender — Build & Delivery Lab

A practical learning project for Java, Gradle, Jenkins, Docker, container security, Kubernetes and operational monitoring.

New to the project? Read the [Beginner’s Guide](BEGINNERS_GUIDE.md) for a plain-English explanation and use the [Glossary](GLOSSARY.md) for the terms used throughout the lab.

The end product is a small browser game where players respond to common homelab incidents. The game is deliberately modest; the main purpose is learning and proving a complete, supportable software delivery path from source to a monitored Kubernetes workload.

## What this project demonstrates

- Java application design and unit testing
- Gradle builds, dependency management and repeatable packaging
- Jenkins Pipeline as Code through a versioned `Jenkinsfile`
- Docker image construction using an isolated Docker-in-Docker builder
- Container vulnerability scanning with Trivy
- A HIGH/CRITICAL security gate that blocks vulnerable images before publication
- Authenticated publication to a private Docker registry
- Authenticated K3s/containerd pulls from that registry
- Restricted Jenkins deployment into Kubernetes
- Kubernetes Deployment, Service, readiness/liveness probes and service-level health verification
- Automatic rollback when deployment verification fails
- Git-owned Kubernetes desired state with approved tag-plus-digest releases
- Prometheus/Grafana monitoring of deployment availability, pod readiness and new restarts
- Traceable releases using Jenkins build numbers and immutable image digests

## Current validated release — build 15

Jenkins build `15` is the current validated healthy release.

Approved immutable identity:

```text
192.168.2.220:5000/homelab-defender:15@sha256:2154a1881acc63db852dbeebc7daf5890a1c9527c4b70837b2ad33fb76ad940b
```

Build 15 used application source revision:

```text
d0e8e8b Merge Kubernetes desired-state documentation alignment
```

The release ran with:

```text
BUILD_CONTAINER=false
PUBLISH_CONTAINER=true
```

`PUBLISH_CONTAINER=true` enables the complete gated release path, so build 15 exercised:

```text
Test
  → Package
  → Containerise
  → Security Scan
  → Publish image
  → Deploy to K3s
  → rollout and /healthz verification
```

Jenkins recorded `SUCCESS` after `1013344 ms`.

Independent post-release validation confirmed:

- Deployment `homelab-defender`: `1/1` available;
- build-15 pod: `Running` with `0` restarts;
- Service: private ClusterIP on port `8080`;
- running image digest: `sha256:2154a1881acc63db852dbeebc7daf5890a1c9527c4b70837b2ad33fb76ad940b`;
- Prometheus observed the build-15 image identity;
- `Homelab Defender Deployment Unavailable` expression = `0`;
- `Homelab Defender New Container Restart` expression = `0`.

The approved tag and digest were reconciled into the authoritative Kubernetes repository through `jrwroberts1976/kubernetes-homelab#11`, merge commit `1565663aa0ed1584a09bdc0761ce5e143bf61cce`.

Operational release evidence is recorded in `jrwroberts1976/home-lab-docs/jenkins/homelab-defender-build-15-validation-2026-08-26.md`.

## Delivery flow

```text
GitHub change
  → Jenkins build
  → Gradle test and package
  → isolated Docker image build
  → Trivy HIGH/CRITICAL vulnerability gate
  → authenticated private registry
  → restricted SSH deployment command
  → authenticated K3s/containerd image pull
  → Kubernetes rolling deployment
  → readiness/liveness probes
  → Service-level /healthz verification
  → automatic rollback on failure
  → Prometheus/Grafana operational observation
  → approved tag/digest reconciliation into kubernetes-homelab
  → future public release through Cloudflare
```

## Platform design

| Component | Location | Purpose |
|---|---|---|
| Source code and pipeline definition | GitHub | Public, version-controlled source and delivery definition |
| Jenkins controller | TestServer | Internal-only CI/CD controller |
| Jenkins Docker builder | TestServer | Isolated Docker-in-Docker daemon used for image builds |
| Trivy | Jenkins security stage | Checks built images for known HIGH/CRITICAL vulnerabilities |
| Private Docker registry | TestServer TCP 5000 | Authenticated image hand-off between Jenkins and K3s |
| Restricted deployment endpoint | `k3s-node-01` SSH | Allows Jenkins to request only a Homelab Defender deployment by build number |
| Kubernetes runtime | `k3s-node-01` | Runs the isolated test deployment |
| Kubernetes desired state | `kubernetes-homelab` | Owns Namespace, Deployment, Service, probes and approved image tag/digest |
| State metrics | kube-state-metrics | Exposes deployment and pod state to Prometheus |
| Operational monitoring | Prometheus + Grafana on `ids-01` | Dashboard and Defender-specific availability/restart alerts |
| Public access | Cloudflare | Planned public game route; Jenkins and the registry remain private |

## Security and operating principles

- Jenkins is intentionally not exposed through Cloudflare or Nginx Proxy Manager.
- Jenkins builds images through an isolated Docker-in-Docker daemon rather than TestServer's host Docker socket.
- Jenkins connects to the builder over TLS using `tcp://docker:2376` and client certificates.
- The Jenkins and Docker builder containers share the internal `homelab_apps` Docker network.
- Trivy `0.72.0` is pinned in the pipeline and runs as a temporary container.
- The Trivy gate scans `HIGH` and `CRITICAL` findings and returns a non-zero exit code when policy is breached, stopping publication.
- The final runtime image uses `eclipse-temurin:21-jre-jammy`.
- The private registry uses htpasswd Basic authentication.
- Jenkins publishes with the dedicated `jenkins-ci` registry account; K3s uses a separate runtime pull identity.
- TestServer firewall access to TCP 5000 is restricted to required sources.
- K3s reads registry credentials and its HTTP endpoint from `/etc/rancher/k3s/registries.yaml`.
- Jenkins deployment uses a dedicated `jenkins-deploy` SSH account constrained by a forced command and narrow sudo rule.
- No registry passwords, TLS client certificates, kubeconfig files or deployment secrets are committed here.
- Images use Jenkins build-number tags instead of `latest`.
- The authoritative Kubernetes desired state, including the approved immutable digest, lives in `jrwroberts1976/kubernetes-homelab`.

## Trivy cache behaviour

Build 15 performed a legitimate vulnerability-database refresh rather than exposing a broken cache.

During the security stage the primary vulnerability-DB mirror returned `BLOB_UNKNOWN`; Trivy automatically fell back to `ghcr.io/aquasecurity/trivy-db:2` and completed successfully. The Java database was also refreshed.

The persistent DinD volume `trivy-cache` was verified after the build:

```text
/root/.cache/trivy/db       1.2G
/root/.cache/trivy/java-db  1.4G
/root/.cache/trivy/fanal    1.0M
Total                       2.6G
```

Recorded metadata showed:

```text
Vulnerability DB downloaded: 2026-08-26T07:28:38Z
Vulnerability DB next update: 2026-08-27T07:03:22Z
Java DB downloaded:          2026-08-26T07:35:54Z
Java DB next update:         2026-08-29T01:07:43Z
```

No Jenkinsfile change is required from this observation.

## Release history

### Build 12 — first gated publication

Build 12 completed the first successful security-gated publication to the private registry and proved K3s/containerd could authenticate to and pull the private image.

```text
192.168.2.220:5000/homelab-defender:12
```

### Build 13 — automatic rollback proof

Build 13 was the first automated deployment attempt. Build, scan, publish and rollout succeeded, but the first Service-level health request received a transient connection reset. The deployment helper restored build 12 and Jenkins marked build 13 failed. This proved the automatic rollback path.

The health verifier was then improved to retry `/healthz` up to 15 times before declaring a release unhealthy.

### Build 14 — first fully automated end-to-end release

Build 14 was the first completely hands-off source-to-healthy-Kubernetes release.

It passed Gradle tests, packaging and the Trivy HIGH/CRITICAL gate, published:

```text
192.168.2.220:5000/homelab-defender:14
```

and completed the restricted K3s deployment with `/healthz` passing on attempt `1/15`.

### Build 15 — first release validated through monitoring and Git reconciliation

Build 15 repeated the complete automated release path while the dedicated Defender dashboard and alert rules were live. The resulting pod was healthy with zero restarts, both Defender alert expressions remained zero, the runtime digest was independently verified, and the approved tag-plus-digest was reconciled into `kubernetes-homelab`.

## Jenkins pipeline

The current Jenkins stages are:

```text
Test
  ↓
Package
  ↓
Containerise
  ↓
Security Scan
  ↓
Publish image
  ↓
Deploy to K3s
```

`BUILD_CONTAINER=true` builds and scans an image without publishing or deploying it.

`PUBLISH_CONTAINER=true` requests the complete gated release path: build, scan, authenticated publish, restricted deployment, rollout wait and service-level health verification. A failed deployment or health check returns Jenkins to failure and the deployment helper attempts to restore the previously running image.

The security stage runs pinned Trivy `0.72.0` with:

```text
--timeout 15m
--skip-version-check
--scanners vuln
--severity HIGH,CRITICAL
--exit-code 1
```

A finding that breaches policy stops the pipeline before publication.

## Kubernetes deployment and source of truth

The authoritative Kubernetes desired state is stored in:

```text
jrwroberts1976/kubernetes-homelab/applications/homelab-defender-test
```

That Kustomization owns the `homelab-defender-test` Namespace, application Deployment, private ClusterIP Service, readiness/liveness probes against `/healthz`, and approved release tag/digest.

The Jenkins release path can temporarily advance the live Deployment by build tag. After a release is validated, its tag and digest must be reconciled into `kubernetes-homelab` so Git remains authoritative. Never apply an older desired-state image over a newer healthy Jenkins deployment; reconcile Git first.

Deployment, verification, reconciliation and rollback instructions are in [`k8s/README.md`](k8s/README.md).

The restricted node-side implementation is versioned at:

```text
ops/deploy-homelab-defender
```

## Monitoring

The Homelab Defender monitoring path is operational:

```text
Homelab Defender on k3s-node-01
        ↓
kube-state-metrics 192.168.2.211:8080
        ↓
Prometheus on ids-01
        ↓
Grafana on ids-01
```

Dedicated Grafana objects:

```text
Dashboard: Homelab Defender Kubernetes Operations
Dashboard UID: homelab-defender-k8s

Alert: Homelab Defender Deployment Unavailable
Alert UID: ffwbnisgmg4cgb

Alert: Homelab Defender New Container Restart
Alert UID: afwbnisiruz28f
```

The current healthy release produces no Defender alert instance. A synthetic firing/email-delivery exercise has not been performed for these two service-specific rules.

## Current status

- ✅ End-to-end Gradle/Jenkins/container delivery path operational
- ✅ HIGH/CRITICAL Trivy gate operational
- ✅ Authenticated private registry publication and K3s pull operational
- ✅ Restricted Jenkins deployment and automatic rollback proven
- ✅ Git-owned Kubernetes desired state established
- ✅ Build 14 proved the first fully automated healthy release
- ✅ Build 15 validated the full release path with runtime digest verification
- ✅ Build 15 reconciled to immutable Git desired state
- ✅ Dedicated Grafana dashboard deployed and validated
- ✅ Defender availability and restart alert rules deployed and healthy
- ✅ Persistent Trivy cache verified
- ⏳ Add a controlled external game route through Cloudflare
- ⏳ Replace Docker's deprecated legacy builder with BuildKit/buildx
- ⏳ Complete remaining Jenkins controller/data recovery and source-ownership documentation

## Next engineering milestones

The delivery and operational monitoring paths are now established. The next useful improvements are:

1. expose the game through a controlled Cloudflare route without exposing Jenkins or the registry;
2. replace Docker's deprecated legacy builder with BuildKit/buildx;
3. complete Jenkins controller/data recovery procedures and bring remaining host-specific Jenkins runtime definitions under controlled source ownership.

## Why this exists

The aim is to learn modern build and delivery practices without pretending that a pipeline alone creates reliable delivery. A useful delivery path has protected credentials, clear ownership boundaries, test evidence, security evidence, immutable release identity, health evidence, monitoring evidence and a rollback path.
