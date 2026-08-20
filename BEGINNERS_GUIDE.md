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
  → pull it into Kubernetes
  → prove it is healthy
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

The current path is:

1. A change is saved in GitHub.
2. Jenkins checks out the exact commit.
3. Jenkins runs the Gradle tests.
4. Jenkins packages the application.
5. When container building is requested, Jenkins creates a Docker image.
6. Trivy scans that image for HIGH and CRITICAL vulnerabilities.
7. If the security check passes, Jenkins may authenticate to the private registry and push the image.
8. K3s/containerd authenticates to that registry and pulls a specific build-numbered image.
9. Kubernetes starts the image in an isolated test namespace.
10. Kubernetes checks `/healthz` to confirm the application is ready and remains healthy.

That gives us a chain of evidence from source commit to running container.

## What we have proved so far

The core delivery path is now working end to end.

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
  → image can be published
```

## The first successful gated publication

Jenkins build 12 completed the whole publish path successfully.

The published image is:

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

The successful pull returned:

```text
Image is up to date for sha256:ef37d59b6c41d99394f053d5f02962e07136f76d7080ab049e5403ce80a8df3e
```

## The first Kubernetes deployment

A separate namespace was created:

```text
homelab-defender-test
```

That gives the application an isolated area inside the cluster instead of mixing it with system workloads.

A Kubernetes `Deployment` was then created using:

```text
192.168.2.220:5000/homelab-defender:12
```

For this first deployment, `imagePullPolicy: Always` was used deliberately. That forces Kubernetes/containerd to check the registry even though the image had already been pulled manually, proving the pod-start path also works with the private registry configuration.

A private `ClusterIP` Service exposes the application inside the cluster on port 8080.

## How Kubernetes knows the app is healthy

The application already exposes:

```text
/healthz
```

and returns:

```text
ok
```

The Deployment uses that endpoint in two ways:

- a **readiness probe** decides when the pod is ready to receive traffic
- a **liveness probe** checks that the application remains alive after startup

The first service-level health test returned:

```text
HTTP/1.1 200 OK

ok
```

The main page also returned the Homelab Defender HTML, so the application is not just running — it is reachable through the Kubernetes Service.

## Why the namespace and Service matter

A **Namespace** is an organisational and isolation boundary inside Kubernetes. Using `homelab-defender-test` makes it obvious that this is a test workload and keeps its objects separate from the cluster's system components.

A **Service** gives pods a stable internal network address. Pods can be replaced during updates, but the Service remains the consistent route to the application.

The current Service type is `ClusterIP`, meaning it is private to the cluster. Nothing has been exposed publicly yet.

## What the Jenkins build choices mean

When starting a build manually, Jenkins offers two parameters:

| Option | What it does |
|---|---|
| `BUILD_CONTAINER` | Runs tests, packages the game, builds a Docker image and runs the Trivy security gate. |
| `PUBLISH_CONTAINER` | Requests the complete gated path, including authenticated publication to the private registry. |

Publishing implies the image must first be built and pass the security gate.

Each image uses the Jenkins build number as its tag. That means build 12 becomes:

```text
homelab-defender:12
```

This is much safer than deploying an ambiguous `latest` tag because we know exactly which Jenkins build Kubernetes is running.

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
K3s/containerd authenticated pull
   ↓
homelab-defender-test namespace
   ↓
Deployment
   ↓
ClusterIP Service
   ↓
readiness + liveness health checks
```

Every major hand-off has now been tested manually and has produced evidence.

## What happens next

The next improvement is not to add more infrastructure immediately. It is to make this successful Kubernetes deployment reproducible.

The Deployment, Service and namespace definition should be stored as versioned Kubernetes YAML in this repository rather than existing only as a command that was pasted into a terminal. Then we can add:

1. a repeatable deployment command
2. a verification command
3. a rollback procedure
4. a decision on whether Jenkins should deploy automatically or require a separate approval
5. monitoring for the running workload
6. finally, a controlled public route through Cloudflare

## Safety boundaries

- Jenkins remains internal-only.
- Jenkins does not receive TestServer's host Docker socket.
- Jenkins-to-builder Docker traffic uses TLS.
- Trivy is temporary and version-pinned.
- A failed security gate prevents publication.
- Registry credentials stay outside Git.
- K3s uses a dedicated registry credential.
- The registry is firewall-restricted.
- The first Kubernetes workload is isolated in `homelab-defender-test`.
- The current Service is private `ClusterIP`, not public.
- Each release uses an immutable build-number tag.

## Useful mental model

Think of it as a small workshop:

- **GitHub** is the design folder.
- **Gradle** is the build instruction sheet.
- **Jenkins** is the workbench.
- **Docker** is the sealed delivery box.
- **Trivy** is the security inspector.
- **The registry** is the locked stockroom.
- **containerd** is the warehouse handler that fetches the approved box.
- **Kubernetes** is the team that puts that box into service and checks it stays healthy.
- **Cloudflare** will eventually be the public reception desk.

The important achievement is that the box can now travel from the workbench all the way into the test environment while passing the required checks at each boundary.
