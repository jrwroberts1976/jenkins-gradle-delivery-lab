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
- The Trivy gate policy is to inspect `HIGH` and `CRITICAL` findings and return a failing exit code when the policy is breached, preventing the publish stage from running.
- Trivy `0.72.0` has been validated as a versioned container against Jenkins' isolated Docker daemon; it is intentionally not installed directly into the Jenkins controller container.
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
- ✅ Trivy `0.72.0` container successfully connected to the isolated Jenkins Docker daemon over TLS on `homelab_apps`
- ✅ `homelab-defender:6` was scanned successfully from the isolated builder
- ✅ Trivy gate behaviour validated: `--exit-code 1` returned exit code `1` for policy-breaking findings
- ⚠️ `homelab-defender:6` currently reports 8 HIGH and 0 CRITICAL findings in `usr/bin/pebble`; the Ubuntu package scan reports 0 HIGH/CRITICAL findings
- ⏳ Remediate the vulnerable runtime/base-image component and rebuild the image
- ⏳ Add the Trivy security scan stage between `Containerise` and `Publish image`
- ⏳ Validate the first immutable Jenkins image push to the registry through the gated pipeline
- ⏳ Configure K3s to pull from the internal registry
- ⏳ Add an isolated K3s test deployment

## Current delivery boundary

Jenkins can test, package and build a Docker image without access to TestServer's host Docker socket. Its Docker client talks to the separate `jenkins-docker` daemon over TLS. The controller and builder are both attached to `homelab_apps`, and Jenkins receives its Docker client certificates through a read-only certificate path inside the controller.

The manual scanner-to-builder validation is now complete. Trivy `0.72.0` ran as a temporary container on `homelab_apps`, used the existing Docker TLS client certificates, and successfully inspected `homelab-defender:6` inside the isolated builder.

The first normal scan required a longer timeout because image analysis exceeded Trivy's default limit. Re-running with `--timeout 15m` completed successfully. The scan reported 0 HIGH/CRITICAL Ubuntu package findings and 8 HIGH findings in the `usr/bin/pebble` Go binary.

The policy behaviour was then proven with `--severity HIGH,CRITICAL --exit-code 1`: Trivy returned exit code `1`, demonstrating that the intended Jenkins security gate can block an image that breaches policy before `Publish image` is reached.

The security stage is not yet committed to the `Jenkinsfile`. The next engineering task is to remediate or replace the vulnerable runtime component, rebuild and rescan the image, and then add the proven gate to the pipeline.

## Security scan design

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

The connection verified for both Jenkins and the temporary Trivy scanner is:

```text
DOCKER_HOST=tcp://docker:2376
DOCKER_TLS_VERIFY=1
DOCKER_CERT_PATH=/certs/client
```

The Trivy container is attached to `homelab_apps`, receives the Docker TLS settings and a read-only mount of the client certificates, and scans `homelab-defender:${BUILD_NUMBER}` in the isolated builder. A `15m` timeout is used because the first manual image analysis exceeded the default Trivy timeout.

The manual validation established both sides of the control: Trivy can reach and inspect the isolated image, and a HIGH/CRITICAL finding can produce a non-zero result suitable for stopping Jenkins before publication.

## Planned milestones

1. Remediate the current `usr/bin/pebble` HIGH findings and rebuild the image.
2. Re-scan and prove a compliant image can return a successful gate result.
3. Add and validate the Jenkins vulnerability gate between `Containerise` and `Publish image`.
4. Push and verify the first image that passes the security gate in the registry.
5. Configure K3s registry access and deploy into an isolated namespace.
6. Add health checks and a rollback path.
7. Add an externally accessible game route through Cloudflare.
8. Document monitoring and operational support.

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

The next pipeline change will insert the Trivy security stage after `Containerise` and before `Publish image`. The manual test has already proven that a policy-breaking image returns a failure result; once the current image is remediated, that proven control can be moved into the Jenkinsfile.

## Why this exists

The aim is to learn modern build and delivery practices without pretending that a pipeline alone creates reliable delivery. A useful pipeline has clear ownership, protected capacity, test evidence, security evidence, safe deployment boundaries and operational feedback.
