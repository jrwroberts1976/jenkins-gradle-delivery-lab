# Homelab Defender — Build & Delivery Lab

A practical learning project for Java, Gradle, Jenkins, Docker, container security and Kubernetes.

New to the project? Read the [Beginner’s Guide](BEGINNERS_GUIDE.md) for a plain-English explanation, and use the [Glossary](GLOSSARY.md) for the terms used throughout the lab.

The end product is a small browser game where players respond to common homelab incidents. The game is deliberately modest; the main purpose is learning and proving a complete, supportable software delivery path.

## What this project demonstrates

- Java application design and unit testing
- Gradle builds, dependency management and repeatable packaging
- Jenkins Pipeline as Code through a versioned `Jenkinsfile`
- Docker image construction using an isolated Docker-in-Docker builder
- Container vulnerability scanning with Trivy
- A security gate that blocks vulnerable images before publication
- Authenticated publication to a private Docker registry
- Authenticated K3s/containerd pulls from that registry
- Kubernetes Deployment, Service and health probes
- Traceable immutable releases using Jenkins build numbers

## Delivery flow

```text
GitHub change
  → Jenkins build
  → Gradle test and package
  → Docker image build
  → Trivy HIGH/CRITICAL vulnerability gate
  → Authenticated private registry
  → Authenticated K3s image pull
  → Isolated Kubernetes deployment
  → Readiness/liveness health checks
  → Future public release through Cloudflare
```

## Platform design

| Component | Location | Purpose |
|---|---|---|
| Source code and pipeline definition | GitHub | Public, version-controlled source and delivery definition |
| Jenkins controller | TestServer | Internal-only CI/CD controller |
| Jenkins Docker builder | TestServer | Isolated Docker-in-Docker daemon used for image builds |
| Trivy | Jenkins security stage | Checks built images for known HIGH/CRITICAL vulnerabilities |
| Private Docker registry | TestServer TCP 5000 | Authenticated image hand-off between Jenkins and K3s |
| Kubernetes runtime | `k3s-node-01` | Runs the isolated test deployment |
| Public access | Cloudflare | Planned public game route; Jenkins and the registry remain private |

## Security and operating principles

- Jenkins is intentionally not exposed through Cloudflare or Nginx Proxy Manager.
- Jenkins builds images through an isolated Docker-in-Docker daemon rather than TestServer's host Docker socket.
- Jenkins connects to the builder over TLS using `tcp://docker:2376` and client certificates.
- The Jenkins and Docker builder containers share the internal `homelab_apps` Docker network.
- Trivy `0.72.0` is pinned in the pipeline and runs as a temporary container rather than being installed permanently in Jenkins.
- The Trivy gate scans `HIGH` and `CRITICAL` findings and returns a non-zero exit code when policy is breached, stopping publication.
- The final runtime image uses `eclipse-temurin:21-jre-jammy`, selected after the previous runtime exposed HIGH findings in `usr/bin/pebble`.
- The private registry runs as the TestServer `docker-registry` host service and uses htpasswd Basic authentication.
- Jenkins publishes with the dedicated `jenkins-ci` registry account. K3s uses a separate registry account for runtime pulls.
- TestServer firewall access to TCP 5000 is restricted to required sources rather than the whole LAN.
- K3s reads registry credentials and the HTTP endpoint from `/etc/rancher/k3s/registries.yaml`.
- No registry passwords, TLS client certificates, kubeconfig files or deployment secrets are committed to this repository.
- Images are tagged with Jenkins build numbers instead of `latest`, so a deployment identifies a specific release.

## Current status

- ✅ Public GitHub repository created
- ✅ Java 21 Gradle project and Gradle Wrapper committed
- ✅ Homelab Defender playable application committed
- ✅ Jenkins controller running internally on TestServer
- ✅ Jenkins Multibranch Pipeline connected to `main`
- ✅ Gradle test and package stages validated
- ✅ Jenkins builds immutable `homelab-defender:<build-number>` images in the isolated builder
- ✅ Vulnerable runtime behaviour demonstrated with `homelab-defender:6` and 8 HIGH findings
- ✅ Runtime remediated to `eclipse-temurin:21-jre-jammy`
- ✅ Clean image behaviour demonstrated with 0 HIGH/CRITICAL findings
- ✅ Automated `Security Scan` stage added to the Jenkinsfile
- ✅ Trivy gate proven to fail policy-breaking images and pass compliant images
- ✅ Authenticated registry publishing proven end to end
- ✅ `homelab-defender:12` published successfully to `192.168.2.220:5000`
- ✅ Registry catalog/tag query confirmed build `12` exists
- ✅ `k3s-node-01` firewall path to the registry validated
- ✅ K3s private-registry configuration loaded successfully
- ✅ containerd authenticated to the HTTP registry and pulled `homelab-defender:12`
- ✅ Isolated namespace `homelab-defender-test` created
- ✅ Kubernetes Deployment rolled out successfully
- ✅ ClusterIP Service created
- ✅ `/healthz` returned HTTP 200 with `ok`
- ✅ Game HTML served successfully through the Kubernetes Service
- ⏳ Commit the Kubernetes deployment manifest to the repository instead of relying on an ad-hoc apply command
- ⏳ Add an explicit rollback procedure and deployment verification commands
- ⏳ Decide whether Jenkins should deploy automatically after publication or keep deployment as a separate approval step
- ⏳ Add a controlled external game route through Cloudflare
- ⏳ Document monitoring and operational support

## Evidence from the first gated release

The project now has a complete working hand-off from CI to Kubernetes.

Jenkins build 12 completed the gated publication path and pushed:

```text
192.168.2.220:5000/homelab-defender:12
```

The image passed the automated Trivy HIGH/CRITICAL gate before the publish stage. Jenkins authenticated to the registry only during publication and logged out afterwards.

K3s initially attempted HTTPS because the private-registry configuration had not yet been loaded by the running service. After restarting K3s, the service reported that it was using `/etc/rancher/k3s/registries.yaml`, and containerd generated an HTTP registry host entry plus authentication configuration.

The pull then succeeded:

```text
Image is up to date for sha256:ef37d59b6c41d99394f053d5f02962e07136f76d7080ab049e5403ce80a8df3e
```

The deployment in namespace `homelab-defender-test` rolled out successfully. The application already exposes `/healthz`, so Kubernetes readiness and liveness probes can use the application itself as health evidence.

The first service-level validation returned:

```text
HTTP/1.1 200 OK

ok
```

and the main page returned the Homelab Defender HTML.

## Current delivery boundary

The system deliberately separates responsibilities:

```text
GitHub
   ↓
Jenkins controller
   ↓ TLS
isolated jenkins-docker daemon
   ↓
Trivy security gate
   ↓ authenticated publish
private registry on TestServer
   ↓ authenticated pull
K3s/containerd on k3s-node-01
   ↓
homelab-defender-test namespace
```

The application is running only in the isolated test namespace. It is not yet exposed publicly.

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
```

`BUILD_CONTAINER` builds and scans an image. `PUBLISH_CONTAINER` requests the full gated build-and-publish path.

The security stage runs pinned Trivy `0.72.0` with:

```text
--timeout 15m
--skip-version-check
--scanners vuln
--severity HIGH,CRITICAL
--exit-code 1
```

A finding that breaches policy stops the pipeline before publication.

## Next engineering milestone

The next useful step is to make the successful Kubernetes deployment reproducible by storing its Namespace/Deployment/Service manifest in this repository. After that, add deployment verification and rollback instructions and decide whether deployment should remain a manual release action or become a separately gated Jenkins stage.

## Why this exists

The aim is to learn modern build and delivery practices without pretending that a pipeline alone creates reliable delivery. A useful delivery path has protected credentials, clear boundaries, test evidence, security evidence, immutable releases, health evidence and a rollback path.
