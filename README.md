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
  → K3s test deployment
  → Health check
  → Public game release through Cloudflare
```

## Platform design

| Component | Location | Purpose |
|---|---|---|
| Source code and pipeline definition | GitHub | Public, version-controlled project source |
| Jenkins controller | TestServer | Internal-only CI/CD controller |
| Kubernetes runtime | k3s-node-01 | Runs the test deployment |
| Public access | Cloudflare | Publishes the game only — never Jenkins |

## Security and operating principles

- Jenkins is intentionally not exposed through Cloudflare or Nginx Proxy Manager.
- No credentials, kubeconfig files or deployment secrets are committed to this repository.
- Container publishing and Kubernetes deployment will remain manual/disabled until scoped credentials and a registry workflow are in place.
- The first pipeline will build and test only; deployment is earned through validation rather than enabled by default.

## Current status

- ✅ Public GitHub repository created
- ✅ Java 21 Gradle project and Gradle Wrapper committed
- ✅ Homelab Defender first playable build committed
- ✅ Jenkins controller running internally on TestServer
- ✅ Jenkins Multibranch Pipeline connected to `main`
- ✅ First Jenkins build completed successfully: test and package stages passed
- ✅ Jenkins build #2 successfully built the Docker image `homelab-defender:2`
- ✅ The image is held in Jenkins’ isolated Docker-in-Docker builder
- ⏳ An image registry and K3s test deployment still need to be configured

## Current delivery boundary

Jenkins can now test, package and build a Docker image without access to
TestServer's host Docker socket. The resulting image is stored in the isolated
Jenkins builder, so it is not yet available to K3s. The next milestone is to
push approved images to a registry reachable by `k3s-node-01`, then deploy
only to an isolated test namespace.

## Planned milestones

1. Create a small Java game and accompanying unit tests.
2. Build and run it locally with Gradle.
3. Add a Jenkinsfile to test and package every Git change.
4. Build a Docker image from successful builds.
5. Deploy into an isolated K3s namespace.
6. Add an externally accessible game route through Cloudflare.
7. Document monitoring, rollback and operational support.

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

---

Status: Jenkins controller installed on TestServer; application scaffold and pipeline are the next steps.
