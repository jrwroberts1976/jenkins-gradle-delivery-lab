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
- Automated Jenkins deployment into Kubernetes
- Kubernetes Deployment, Service and health probes
- Automatic rollback when deployment verification fails
- Reproducible Kubernetes baseline manifests stored in Git
- Traceable releases using Jenkins build numbers

## Delivery flow

```text
GitHub change
  → Jenkins build
  → Gradle test and package
  → Docker image build
  → Trivy HIGH/CRITICAL vulnerability gate
  → Authenticated private registry
  → Restricted SSH deployment command
  → Authenticated K3s image pull
  → Kubernetes rolling deployment
  → Service-level /healthz verification
  → Automatic rollback on failure
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
| Restricted deployment endpoint | `k3s-node-01` SSH | Allows Jenkins to request only a Homelab Defender deployment by build number |
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
- Jenkins deployment uses a dedicated SSH key and the `jenkins-deploy` account. The key is constrained by a forced command and cannot be used as a normal interactive shell.
- The forced command validates a numeric build number and may invoke only the root-owned Homelab Defender deployment script through a narrow sudo rule.
- No registry passwords, TLS client certificates, kubeconfig files or deployment secrets are committed to this repository.
- Images are tagged with Jenkins build numbers instead of `latest`, so a deployment identifies a specific Jenkins release.
- Git stores the Kubernetes baseline structure under `k8s/`; Jenkins supplies the live release image tag during automated deployment.

## Current status

- ✅ Public GitHub repository created
- ✅ Java 21 Gradle project and Gradle Wrapper committed
- ✅ Homelab Defender playable application committed
- ✅ Jenkins controller running internally on TestServer
- ✅ Jenkins Multibranch Pipeline connected to `main`
- ✅ Gradle test and package stages validated
- ✅ Jenkins builds `homelab-defender:<build-number>` images in the isolated builder
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
- ✅ Kubernetes Deployment and private ClusterIP Service created
- ✅ `/healthz` returned HTTP 200 with `ok`
- ✅ Game HTML served successfully through the Kubernetes Service
- ✅ Reproducible Namespace/Deployment/Service baseline committed at `k8s/homelab-defender-test.yaml`
- ✅ Deployment, verification and rollback procedure documented in `k8s/README.md`
- ✅ Restricted `jenkins-deploy` SSH path proven from Jenkins/TestServer to `k3s-node-01`
- ✅ Build 13 proved automatic rollback: publish and rollout succeeded, a transient service-level health check failed, and the deployment returned safely to build 12
- ✅ Deployment verification improved to retry `/healthz` before declaring failure
- ✅ Jenkins build 14 completed the first fully automated source-to-healthy-Kubernetes release
- ✅ Build 14 passed tests, packaging and Trivy with 0 HIGH/CRITICAL findings
- ✅ Build 14 published `192.168.2.220:5000/homelab-defender:14`
- ✅ Build 14 rolled out automatically to K3s and `/healthz` passed on attempt 1/15
- ⏳ Add monitoring and operational support for the running Kubernetes workload
- ⏳ Add a controlled external game route through Cloudflare
- ⏳ Replace Docker's deprecated legacy builder with BuildKit/buildx

## Evidence from the first gated release

Jenkins build 12 completed the first gated publication path and pushed:

```text
192.168.2.220:5000/homelab-defender:12
```

The image passed the automated Trivy HIGH/CRITICAL gate before the publish stage. Jenkins authenticated to the registry only during publication and logged out afterwards.

K3s initially attempted HTTPS because the private-registry configuration had not yet been loaded by the running service. After restarting K3s, the service reported that it was using `/etc/rancher/k3s/registries.yaml`, and containerd generated an HTTP registry host entry plus authentication configuration.

The pull then succeeded:

```text
Image is up to date for sha256:ef37d59b6c41d99394f053d5f02962e07136f76d7080ab049e5403ce80a8df3e
```

The deployment in namespace `homelab-defender-test` rolled out successfully. The application already exposes `/healthz`, so Kubernetes readiness and liveness probes use the application itself as health evidence.

The first service-level validation returned:

```text
HTTP/1.1 200 OK

ok
```

and the main page returned the Homelab Defender HTML.

## Evidence from automatic rollback

Jenkins build 13 was the first automated deployment attempt. The build, Trivy scan, registry push and Kubernetes rollout all succeeded, but the first ClusterIP health request returned a transient connection reset.

The root-owned deployment script treated that as a failed release and automatically restored:

```text
192.168.2.220:5000/homelab-defender:12
```

Kubernetes rolled the previous image back successfully and Jenkins marked build 13 as failed. This proved that deployment failure does not leave an unverified release in service.

The health verifier was then changed to retry `/healthz` up to 15 times with a short delay, allowing the Service datapath time to converge while still failing and rolling back if the application never becomes reachable.

## First fully automated end-to-end release

Jenkins build 14 proved the complete delivery chain without a manual Kubernetes deployment step.

The build checked out commit `cc1b25a4821e07fa647b3f807c8f2c8cd69a99cc`, ran the Gradle tests and package stages, built `homelab-defender:14`, and scanned it with Trivy. The security report contained 0 HIGH/CRITICAL findings.

Jenkins then authenticated to the private registry and pushed:

```text
192.168.2.220:5000/homelab-defender:14
```

The `Deploy to K3s` stage used the restricted `jenkins-deploy` SSH credential to request build 14. The deployment script changed the Deployment image from build 12 to build 14, waited for the Kubernetes rollout, then checked the application through the ClusterIP Service.

The final verification was:

```text
Health check passed on attempt 1/15.
Deployment of 192.168.2.220:5000/homelab-defender:14 completed successfully.
Finished: SUCCESS
```

This proves the current end-to-end path:

```text
GitHub
   ↓
Jenkins
   ↓
Gradle tests + package
   ↓
isolated Docker build
   ↓
Trivy security gate
   ↓
authenticated private-registry publish
   ↓
restricted deployment request
   ↓
K3s/containerd authenticated pull
   ↓
Kubernetes rollout
   ↓
ClusterIP /healthz verification
   ↓
SUCCESS
```

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

`PUBLISH_CONTAINER=true` requests the complete gated release path: build, scan, authenticated publish, restricted deployment, rollout wait and service-level health verification. A failed deployment or health check returns Jenkins to failure and the deployment script attempts to restore the previously running image.

The security stage runs pinned Trivy `0.72.0` with:

```text
--timeout 15m
--skip-version-check
--scanners vuln
--severity HIGH,CRITICAL
--exit-code 1
```

A finding that breaches policy stops the pipeline before publication.

## Kubernetes deployment

The reproducible Kubernetes baseline is stored at:

```text
k8s/homelab-defender-test.yaml
```

It defines the `homelab-defender-test` Namespace, the application Deployment, a private ClusterIP Service, and readiness/liveness probes against `/healthz`.

The baseline manifest is used to create or recover the Kubernetes object structure. The live release image is selected by the Jenkins deployment stage using the Jenkins build number. This means the YAML structure remains version-controlled while release state is traceable through the Jenkins build and the Deployment's `kubernetes.io/change-cause` annotation.

Deployment, verification and rollback instructions are in [`k8s/README.md`](k8s/README.md).

The root-owned deployment implementation used by the restricted Jenkins SSH path is versioned at:

```text
ops/deploy-homelab-defender
```

## Next engineering milestone

The core delivery lab is now end to end. The next useful work is operational rather than adding another delivery stage: monitor the Kubernetes workload, surface failed/restarted pods and unhealthy deployments in the existing monitoring stack, and then decide how the game should be exposed externally through a controlled route.

A separate technical cleanup is to replace Docker's deprecated legacy builder with BuildKit/buildx.

## Why this exists

The aim is to learn modern build and delivery practices without pretending that a pipeline alone creates reliable delivery. A useful delivery path has protected credentials, clear boundaries, test evidence, security evidence, versioned releases, health evidence and a rollback path.
