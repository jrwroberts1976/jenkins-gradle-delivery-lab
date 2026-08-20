# Homelab Defender — Beginner’s Guide

This project is a safe place to learn how a small application moves from an idea in GitHub to a running service.

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
  → roll back automatically if verification fails
```

Each step gives us evidence that the next step is safe to attempt.

## The parts and what they mean

| Name | Plain-English meaning | Where it runs |
|---|---|---|
| GitHub | The shared project folder and change history | Internet |
| Java | The language used to write the game | Source code |
| Gradle | The repeatable build recipe | Jenkins and developer workstation |
| Tests | Automatic checks that the game still behaves as expected | Jenkins |
| Jenkins | The automated build and delivery worker | TestServer, internal only |
| Docker | A standard package containing the app and what it needs to run | Jenkins builder |
| Trivy | A security inspector that checks the Docker image for known vulnerabilities | Jenkins delivery process |
| Private registry | A protected storage shelf for approved Docker images | TestServer LAN |
| containerd | The container runtime K3s uses to pull and run images | k3s-node-01 |
| K3s / Kubernetes | The platform that runs the packaged game | k3s-node-01 |
| Cloudflare | The planned future public front door for the game | Internet edge |

## What happens when we publish a build

The current path is now fully automated when `PUBLISH_CONTAINER=true` is selected:

1. Jenkins checks out the exact GitHub commit.
2. Jenkins runs the Gradle tests.
3. Jenkins packages the application.
4. Jenkins builds a Docker image tagged with the Jenkins build number.
5. Trivy scans that image for HIGH and CRITICAL vulnerabilities.
6. If the security check passes, Jenkins authenticates to the private registry and pushes the image.
7. Jenkins opens a restricted SSH connection to `k3s-node-01` and requests only `deploy <BUILD_NUMBER>`.
8. K3s/containerd authenticates to the registry and pulls the requested image.
9. Kubernetes performs a rolling update in the isolated test namespace.
10. The deployment script waits for the rollout, then checks `/healthz` through the ClusterIP Service.
11. If verification never succeeds, the script attempts to restore the image that was running before the release.

That gives us a chain of evidence from source commit to a healthy running Kubernetes workload.

## The security gate

We first proved the failure side of the security gate. An older image, `homelab-defender:6`, contained 8 HIGH findings in `usr/bin/pebble`. Trivy was configured with `--exit-code 1`, so those findings produced a failing result that could stop the pipeline before publication.

The runtime was then changed to:

```text
eclipse-temurin:21-jre-jammy
```

A rebuilt image returned 0 HIGH/CRITICAL findings under the project policy. The same Trivy command was then added to the Jenkins pipeline as an automatic `Security Scan` stage.

That proved both sides of the control:

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

## The first successful gated publication

Jenkins build 12 completed the first gated publish path successfully.

The published image was:

```text
192.168.2.220:5000/homelab-defender:12
```

The registry returned the build tag when queried, proving that the image was really stored there.

Jenkins does not keep the registry password in the repository. It retrieves the credential from Jenkins Credentials only during the publish stage and logs out afterwards.

## Why the registry has separate accounts

The private registry uses HTTP Basic authentication backed by an `htpasswd` file.

Jenkins uses a dedicated `jenkins-ci` service account for publishing. K3s uses a separate registry account for image pulls. This avoids reusing the Jenkins publishing credential inside the runtime platform.

The registry is also protected by the TestServer firewall. TCP port 5000 is allowed only from the systems and networks that need it rather than being opened to the whole LAN.

## How K3s reaches the registry

K3s uses containerd as its container runtime. Its private-registry settings live in:

```text
/etc/rancher/k3s/registries.yaml
```

The configuration tells K3s two important things:

- this registry uses `http://192.168.2.220:5000`
- use the dedicated registry username/password when pulling images

K3s reads this file when the service starts. During the first test, the file existed but the running K3s service had not yet loaded it, so containerd tried the registry over HTTPS and failed with:

```text
http: server gave HTTP response to HTTPS client
```

After restarting K3s, the service explicitly reported that it was using the private registry config. containerd then generated a host configuration containing the HTTP endpoint and the pull succeeded.

## The Kubernetes test environment

A separate namespace was created:

```text
homelab-defender-test
```

That gives the application an isolated area inside the cluster instead of mixing it with system workloads.

A Kubernetes `Deployment` manages the application pod, and a private `ClusterIP` Service exposes the application inside the cluster on port 8080.

The application exposes:

```text
/healthz
```

and returns:

```text
ok
```

The Deployment uses that endpoint for:

- a **readiness probe**, which decides when the pod is ready to receive traffic
- a **liveness probe**, which checks that the application remains alive after startup

The automated deployment script also checks the same endpoint through the Service after the rollout. That proves not only that the process is running, but that the Kubernetes network path to the application works.

## Why Jenkins does not get full Kubernetes access

Jenkins needs to deploy the application, but it does not need unrestricted administrator access to the cluster.

A dedicated account named `jenkins-deploy` is used for SSH. Its key is constrained by a forced command, so it cannot open a normal interactive shell.

The only accepted request is effectively:

```text
deploy <BUILD_NUMBER>
```

That request invokes a root-owned deployment script through a narrow sudo rule. The script validates the build number and only operates on the Homelab Defender deployment.

This gives the pipeline enough authority to release this application without handing Jenkins general-purpose root or Kubernetes administration access.

## The rollback test — build 13

Build 13 was the first automatic deployment attempt.

The tests passed, the image built, Trivy reported 0 HIGH/CRITICAL findings, the registry push succeeded, and Kubernetes completed the rollout to build 13.

The very first Service-level `/healthz` request then received a transient connection reset. At that point the deployment script did what it was designed to do:

```text
Deployment failed.
→ restore previous image
→ wait for rollback rollout
→ mark Jenkins build as FAILURE
```

Build 12 was restored successfully.

That failure was useful evidence: the automatic rollback path really works.

The health check was then improved so it retries up to 15 times before declaring the release unhealthy. This allows for a short delay while Kubernetes Service networking converges without hiding a genuine persistent failure.

## The first fully automated end-to-end release — build 14

Build 14 completed the entire chain successfully.

Jenkins:

- checked out the expected Git commit
- passed the Gradle tests
- packaged the application
- built `homelab-defender:14`
- ran Trivy and found 0 HIGH/CRITICAL vulnerabilities
- authenticated to the private registry
- published `192.168.2.220:5000/homelab-defender:14`
- connected through the restricted deployment account
- updated Kubernetes from build 12 to build 14
- waited for the rollout
- checked `/healthz` through the Service

The final result was:

```text
Health check passed on attempt 1/15.
Deployment of 192.168.2.220:5000/homelab-defender:14 completed successfully.
Finished: SUCCESS
```

That is the first completely hands-off source-to-healthy-Kubernetes release in the project.

## What the Jenkins build choices mean

When starting a build manually, Jenkins offers two parameters:

| Option | What it does |
|---|---|
| `BUILD_CONTAINER` | Runs tests, packages the game, builds a Docker image and runs the Trivy security gate. It does not publish or deploy. |
| `PUBLISH_CONTAINER` | Runs the complete release path: build, scan, authenticated registry publication, restricted Kubernetes deployment, rollout wait and health verification. |

Publishing implies the image must first be built and pass the security gate, so `PUBLISH_CONTAINER=true` is enough for a full release.

Each release uses the Jenkins build number as its image tag, for example:

```text
homelab-defender:14
```

This is safer than using an ambiguous moving `latest` tag because the running image can be traced directly back to a Jenkins build.

## Git and the live release version

The Kubernetes files in Git define the **baseline structure** of the environment: Namespace, Deployment shape, Service and probes.

The actual live image number is supplied by Jenkins at release time. That means the structure is version-controlled in Git while the current release is traceable through:

- the Jenkins build number
- the Deployment image field
- the `kubernetes.io/change-cause` annotation

This distinction matters because blindly reapplying an older baseline YAML can set the image back to the tag stored in that file. Normal releases should now go through Jenkins.

## The current delivery chain

```text
GitHub
   ↓
Jenkins
   ↓
Gradle tests and package
   ↓
isolated Docker builder
   ↓
Trivy security gate
   ↓
authenticated private registry
   ↓
restricted SSH deployment request
   ↓
K3s/containerd authenticated pull
   ↓
homelab-defender-test namespace
   ↓
Kubernetes rolling Deployment
   ↓
ClusterIP Service
   ↓
readiness + liveness probes
   ↓
service-level /healthz verification
   ↓
SUCCESS or automatic rollback
```

Every major hand-off in this chain has now been exercised.

## What happens next

The delivery mechanism itself is now end to end. The next improvements are operational:

1. monitor the Kubernetes workload and surface pod restarts, failed rollouts and health problems
2. decide how to expose the game through a controlled Cloudflare route
3. replace Docker's deprecated legacy builder with BuildKit/buildx
4. continue documenting release and recovery evidence as the project evolves

## Safety boundaries

- Jenkins remains internal-only.
- Jenkins does not receive TestServer's host Docker socket.
- Jenkins-to-builder Docker traffic uses TLS.
- Trivy is temporary and version-pinned.
- A failed security gate prevents publication.
- Registry credentials stay outside Git.
- K3s uses a dedicated registry credential.
- The registry is firewall-restricted.
- Jenkins deployment uses a dedicated restricted SSH identity rather than general cluster-admin access.
- The Kubernetes workload is isolated in `homelab-defender-test`.
- The Service remains private `ClusterIP`, not public.
- A failed rollout or persistent health-check failure triggers a rollback attempt.
- Releases use explicit Jenkins build-number tags rather than `latest`.

## Useful mental model

Think of it as a small workshop:

- **GitHub** is the design folder.
- **Gradle** is the build instruction sheet.
- **Jenkins** is the workbench and delivery operator.
- **Docker** is the sealed delivery box.
- **Trivy** is the security inspector.
- **The registry** is the locked stockroom.
- **containerd** is the warehouse handler that fetches the approved box.
- **Kubernetes** is the team that puts that box into service and checks it stays healthy.
- **The rollback script** is the recovery plan that puts the previous known-good box back if the new one cannot be verified.
- **Cloudflare** will eventually be the public reception desk.

The important achievement is that a release can now travel from source code all the way into the test environment automatically, while passing security and health checks at each boundary and retaining a recovery path.
