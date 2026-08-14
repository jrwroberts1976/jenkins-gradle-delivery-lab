# Homelab Defender — Build & Delivery Lab

A practical learning project for Java, Gradle, Jenkins, Docker and Kubernetes.

The end product will be a small browser game where players respond to common homelab incidents — failed backups, disk pressure, suspicious requests and pending security updates. The game is deliberately modest; the real focus is learning a complete, supportable delivery path.

## What this project will demonstrate

- Java application design and unit testing
- Gradle builds, dependency management and repeatable packaging
- Jenkins Pipeline as Code through a versioned `Jenkinsfile`
- Docker image construction
- Kubernetes deployment to a non-production K3s namespace
- Health checks, release gates and basic operational ownership

## Target delivery flow

```text
GitHub change
  → Jenkins build
  → Gradle test and package
  → Docker image build
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
| Private container registry | TestServer LAN | Authenticated image hand-off between Jenkins and K3s |
| Kubernetes runtime | k3s-node-01 | Runs the test deployment |
| Public access | Cloudflare | Publishes the game only — never Jenkins |

## Security and operating principles

- Jenkins is intentionally not exposed through Cloudflare or Nginx Proxy Manager.
- The private registry is LAN-only and protected with htpasswd authentication.
- The registry's `jenkins-ci` service account is for Jenkins image publishing; its password is held outside Git.
- Docker trusts only the internal registry as an HTTP exception; it does not relax registry security generally.
- No credentials, kubeconfig files or deployment secrets are committed to this repository.
- Container publishing and Kubernetes deployment remain disabled until Jenkins and K3s have scoped credentials and a registry workflow.
- Deployment is earned through validation rather than enabled by default.

## Current status

- ✅ Public GitHub repository created
- ✅ Java 21 Gradle project and Gradle Wrapper committed
- ✅ Homelab Defender first playable build committed
- ✅ Jenkins controller running internally on TestServer
- ✅ Jenkins Multibranch Pipeline connected to `main`
- ✅ First Jenkins build completed successfully: test and package stages passed
- ✅ Jenkins build #2 successfully built the Docker image `homelab-defender:2`
- ✅ The image is held in Jenkins’ isolated Docker-in-Docker builder
- ✅ An authenticated private registry is already running on the TestServer LAN
- ✅ Dedicated `jenkins-ci` registry account created
- ✅ TestServer Docker and Jenkins’ isolated Docker builder trust the internal registry
- ⏳ Store the `jenkins-ci` credential in Jenkins
- ⏳ Push an immutable Jenkins image tag to the registry
- ⏳ Configure K3s to pull from the internal registry
- ⏳ Add an isolated K3s test deployment

## Current delivery boundary

Jenkins can test, package and build a Docker image without access to TestServer's host Docker socket. The isolated Docker builder and the TestServer Docker daemon are configured to trust the authenticated internal registry.

The remaining work is to store the `jenkins-ci` credential in Jenkins, update the pipeline to log in and push immutable build-number tags, then configure K3s to pull only those images into an isolated test namespace.

## Planned milestones

1. Store the registry credential in Jenkins and push a successful image.
2. Configure K3s registry access and deploy into an isolated namespace.
3. Add health checks and a rollback path.
4. Add an externally accessible game route through Cloudflare.
5. Document monitoring and operational support.

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

The initial pipeline runs `./gradlew clean test` and packages the application distribution. Container building is deliberately disabled unless the `BUILD_CONTAINER` parameter is selected.

## Why this exists

The aim is to learn modern build and delivery practices without pretending that a pipeline alone creates reliable delivery. A useful pipeline has clear ownership, protected capacity, test evidence, safe deployment boundaries and operational feedback.
