# Homelab Defender — Build & Delivery Lab

A practical learning project for Java, Gradle, Jenkins, Docker, container security and Kubernetes.

New to the project? Read the [Beginner’s Guide](BEGINNERS_GUIDE.md) for a plain-English explanation of what each part does and where we are.

The end product will be a small browser game where players respond to common homelab incidents — failed backups, disk pressure, suspicious requests and pending security updates. The game is deliberately modest; the real focus is learning a complete, supportable delivery path.

## What this project will demonstrate

- Java application design and unit testing
- Gradle builds, dependency management and repeatable packaging
- Jenkins Pipeline as Code through a versioned `Jenkinsfile`
- Docker image construction
- Container vulnerability scanning with Trivy
- Security gates that can stop a vulnerable image before publication
- Kubernetes deployment to a non-production K3s namespace
- Health checks, release gates and basic operational ownership

## Target delivery flow

```text
GitHub change
  → Jenkins build
  → Gradle test and package
  → Docker image build
  → Trivy vulnerability scan
  → Private registry
  → K3s test deployment
  → Health check
  → Public game release through Cloudflare
```

## Platform design

| Component | Location | Purpose |
|---|---|---|
| Source code and pipeline definition | GitHub | Public, version-controlled project source |
| Jenkins controller | TestServer | Internal-only CI/CD controller |
| Jenkins Docker builder | TestServer | Isolated Docker-in-Docker daemon used for image builds |
| Trivy | Security scanning stage | Checks built images for known vulnerabilities before publication |
| Private container registry | TestServer LAN | Authenticated image hand-off between Jenkins and K3s |
| Kubernetes runtime | k3s-node-01 | Runs the test deployment |
| Public access | Cloudflare | Publishes the game only — never Jenkins |

## Security and operating principles

- Jenkins is intentionally not exposed through Cloudflare or Nginx Proxy Manager.
- Jenkins builds images through an isolated Docker-in-Docker daemon rather than TestServer's host Docker socket.
- Jenkins connects to that Docker daemon over TLS using `tcp://docker:2376` and client certificates.
- The Jenkins and Docker builder containers share the internal `homelab_apps` Docker network.
- The next pipeline enhancement is a Trivy security gate between image construction and publication.
- The planned Trivy gate will inspect `HIGH` and `CRITICAL` findings and return a failing exit code when the configured policy is breached, preventing the publish stage from running.
- Trivy is already installed on TestServer (`0.72.0`); it is intentionally not installed directly into the Jenkins controller container. The preferred design is to run the scanner as a versioned container against Jenkins' isolated Docker daemon.
- The private registry is LAN-only and protected with htpasswd authentication.
- The registry's `jenkins-ci` service account is for Jenkins image publishing; its password is held outside Git.
- Docker trusts only the internal registry as an HTTP exception; it does not relax registry security generally.
- The firewall allows registry access only from the Jenkins Docker network; registry authentication is still required.
- No credentials, TLS client certificates, kubeconfig files or deployment secrets are committed to this repository.
- Deployment is earned through validation rather than enabled by default.

## Current status

- ✅ Public GitHub repository created
- ✅ Java 21 Gradle project and Gradle Wrapper committed
- ✅ Homelab Defender first playable build committed
- ✅ Jenkins controller running internally on TestServer
- ✅ Jenkins Multibranch Pipeline connected to `main`
- ✅ Jenkins test and package stages validated
- ✅ Jenkins successfully builds immutable `homelab-defender:<build-number>` Docker images
- ✅ The image is held in Jenkins’ isolated Docker-in-Docker builder
- ✅ An authenticated private registry is running on the TestServer LAN
- ✅ Dedicated `jenkins-ci` registry account created
- ✅ TestServer Docker and Jenkins’ isolated Docker builder trust the internal registry
- ✅ Firewall access is restricted to the Jenkins Docker network and validated against the registry
- ✅ The `homelab-registry` credential is stored in Jenkins
- ✅ Jenkins-to-Docker TLS configuration verified (`DOCKER_HOST=tcp://docker:2376`, TLS verification enabled)
- ✅ Trivy `0.72.0` is available on TestServer
- ⏳ Validate Trivy as a container against the Jenkins Docker-in-Docker daemon
- ⏳ Add the Trivy security scan stage between `Containerise` and `Publish image`
- ⏳ Validate the first immutable Jenkins image push to the registry through the gated pipeline
- ⏳ Configure K3s to pull from the internal registry
- ⏳ Add an isolated K3s test deployment

## Current delivery boundary

Jenkins can test, package and build a Docker image without access to TestServer's host Docker socket. Its Docker client talks to the separate `jenkins-docker` daemon over TLS. The controller and builder are both attached to `homelab_apps`, and Jenkins receives its Docker client certificates through a read-only certificate path inside the controller.

Before the private registry becomes the next hand-off, we are adding a vulnerability inspection boundary. Trivy will run as a versioned container and inspect the image held by the same isolated Docker daemon. The intended policy is to fail the Jenkins stage on configured `HIGH` or `CRITICAL` vulnerabilities. A failed security scan therefore stops the pipeline before `Publish image` can run.

This design keeps the scanner out of the Jenkins controller image, makes the scanner version explicit, preserves the isolated builder boundary, and turns vulnerability checking into reproducible pipeline evidence.

## Planned security scan

The intended flow is:

```text
Gradle tests
    ↓
Application package + fingerprint
    ↓
Docker image build
    ↓
Trivy HIGH/CRITICAL vulnerability gate
    ↓
Authenticated private registry publish
```

The connection already verified for the Jenkins Docker client is:

```text
DOCKER_HOST=tcp://docker:2376
DOCKER_TLS_VERIFY=1
DOCKER_CERT_PATH=/certs/client
```

The Trivy container will be attached to `homelab_apps`, receive the Docker TLS settings and a read-only mount of the client certificates, and scan `homelab-defender:${BUILD_NUMBER}` in the isolated builder.

The security stage is not yet committed to the `Jenkinsfile`; the next validation is to prove this scanner-to-builder connection manually before changing the pipeline.

## Planned milestones

1. Validate a containerised Trivy scan against Jenkins' isolated Docker builder.
2. Add and validate the Jenkins vulnerability gate.
3. Push and verify the first image that passes the security gate in the registry.
4. Configure K3s registry access and deploy into an isolated namespace.
5. Add health checks and a rollback path.
6. Add an externally accessible game route through Cloudflare.
7. Document monitoring and operational support.

## Jenkins setup

Jenkins runs internally on TestServer. It is not published through Cloudflare or Nginx Proxy Manager.

Create the job as a **Multibranch Pipeline**:

1. Select **New Item** and name it `homelab-defender`.
2. Select **Multibranch Pipeline**.
3. Under **Branch Sources**, choose **Git**.
4. Use the public repository URL:

   ```text
   https://github.com/jrwroberts1976/jenkins-gradle-delivery-lab.git
   ```

   No GitHub credential is required while the repository remains public.

5. Keep the script path as `Jenkinsfile`.
6. Under **Scan Multibranch Pipeline Triggers**, configure:

   ```text
   H/5 * * * *
   ```

   Jenkins will scan the repository every five minutes and discover branches containing a `Jenkinsfile`.

The pipeline runs `./gradlew clean test` and packages the application distribution. Container building is disabled unless `BUILD_CONTAINER` is selected. To publish, select `PUBLISH_CONTAINER`; the pipeline builds an immutable `homelab-defender:<build-number>` image, retrieves the `homelab-registry` credential only within the publish stage, pushes it to the internal registry, and logs out.

The next change will insert the Trivy security stage after `Containerise` and before `Publish image`. This means a publish request will still build the image, but the registry push will only be reached if the security gate succeeds.

## Why this exists

The aim is to learn modern build and delivery practices without pretending that a pipeline alone creates reliable delivery. A useful pipeline has clear ownership, protected capacity, test evidence, security evidence, safe deployment boundaries and operational feedback.
