# Homelab Defender — Beginner’s Guide

This project is a safe place to learn how a small application moves from an idea in GitHub to a running service.

The application is **Homelab Defender**, a small browser game based on familiar operational problems such as failed backups, low disk space and security updates. The game is useful, but the main goal is learning the delivery process behind it.

## The simple picture

```text
Write code → test it → package it → put it in a container
→ security-check the container → store it safely → run it on Kubernetes
```

Each step gives us evidence that the next step is safe to attempt.

## The parts and what they mean

| Name | Plain-English meaning | Where it runs |
|---|---|---|
| GitHub | The shared project folder and change history | Internet |
| Java | The language used to write the game | Source code |
| Gradle | The repeatable build recipe | Jenkins and developer workstation |
| Tests | Small automatic checks that the game still behaves as expected | Jenkins |
| Jenkins | The automated build worker | TestServer, internal only |
| Docker | A standard package containing the app and what it needs to run | Jenkins |
| Trivy | A security inspector that checks the Docker package for known vulnerabilities | Jenkins delivery process |
| Private registry | A protected storage shelf for Docker packages that have passed the required checks | TestServer LAN |
| K3s / Kubernetes | The platform that will run the packaged game | k3s-node-01 |
| Cloudflare | The future public front door for the game | Internet edge |

## Why use all of this?

Without a delivery path, deploying software often means following a list of manual steps and hoping none are missed.

With this project:

1. A change is saved in GitHub.
2. Jenkins notices the change.
3. Jenkins runs the tests.
4. If the tests pass, Jenkins packages the application.
5. When explicitly requested, Jenkins creates a Docker image.
6. Trivy checks that Docker image for known security vulnerabilities.
7. If the security policy is satisfied, Jenkins can put the image in the private registry.
8. Kubernetes will later pull that exact image and run it in a test area.

That means we can trace a running version back to a specific Git commit and Jenkins build, and we can also show that the image passed an automated security check before release.

## Where we are today

We have completed the core build path and have now proved the security scanner can inspect images inside the isolated Jenkins Docker builder:

- The Java game and its Gradle build are in GitHub.
- Jenkins is running internally on TestServer.
- Jenkins has proved it can run tests, create application packages and build a Docker image.
- Jenkins uses its own isolated Docker builder rather than controlling TestServer’s host Docker socket.
- Jenkins talks to that builder securely over TLS.
- Both Jenkins and its Docker builder use the internal `homelab_apps` network.
- A protected private registry runs on the TestServer LAN.
- A dedicated `jenkins-ci` account exists for publishing images.
- The TestServer firewall permits registry access from the Jenkins Docker network only; the registry still requires a username and password.
- The Jenkins registry credential is stored in Jenkins, not in this repository.
- Trivy version `0.72.0` is available and has now been tested as a temporary container.
- Trivy successfully connected to the same isolated Docker builder over TLS and scanned `homelab-defender:6`.
- The Ubuntu package scan reported 0 HIGH/CRITICAL vulnerabilities.
- A Go binary inside the image, `usr/bin/pebble`, reported 8 HIGH and 0 CRITICAL findings.
- Running Trivy with `--exit-code 1` returned exit code `1`, proving the security policy can reject an image before publication.
- The Trivy scan needed a `15m` timeout because the first full analysis exceeded the default timeout.
- The security stage is not yet committed to the Jenkins pipeline. The next task is to remediate the vulnerable runtime component, rebuild, rescan and then add the proven gate to Jenkins.

## What is the new security check?

Building successfully does not automatically mean a Docker image is safe to release. The application tests tell us whether our application behaves as expected, but they do not tell us whether software inside the container has known security vulnerabilities.

That is where **Trivy** comes in.

Trivy examines the built container image and reports known vulnerabilities. We are making it a Jenkins **security gate**.

The pipeline will become:

```text
Tests
  ↓
Package
  ↓
Build Docker image
  ↓
Trivy security scan
  ↓
Publish to private registry
```

The important word is **gate**. The scan is not just a report that somebody might forget to read. Jenkins will use Trivy's result to decide whether it is allowed to continue.

The rule is:

```text
Security check passes
        ↓
Jenkins may publish the image

Security check fails
        ↓
Pipeline stops
        ↓
Image is NOT published
```

We have now proved the failure side of that rule manually. Trivy scanned `HIGH` and `CRITICAL` findings in `homelab-defender:6`, found eight HIGH vulnerabilities and returned exit code `1` when configured with `--exit-code 1`. That is exactly the kind of result Jenkins can use to stop the later publish stage.

## Why run Trivy in a container?

Jenkins itself runs in a container, and its Docker images are built by another container called `jenkins-docker`.

A simplified picture is:

```text
TestServer

Jenkins controller
      │
      │ secure TLS connection
      ▼
Jenkins Docker builder
      │
      └── homelab-defender:<build-number>
```

Rather than installing more software permanently inside the Jenkins controller, we start a temporary, known version of Trivy when a scan is needed.

That gives us:

```text
Jenkins
   │
   ├── asks Docker builder to build the image
   │
   └── starts Trivy 0.72.0
             │
             └── scans the image in the Docker builder
```

When the scan is finished, the temporary Trivy container disappears.

This keeps Jenkins simpler and makes it obvious which scanner version was used.

## How Jenkins reaches its Docker builder securely

We verified that Jenkins is configured with:

```text
DOCKER_HOST=tcp://docker:2376
DOCKER_TLS_VERIFY=1
DOCKER_CERT_PATH=/certs/client
```

In plain English:

- `docker:2376` is the private address Jenkins uses to reach its Docker builder.
- `TLS_VERIFY=1` means Jenkins verifies the secure connection rather than blindly trusting it.
- `/certs/client` contains the client certificates used for that secure connection.

The manual Trivy test used the same `homelab_apps` network, the same Docker host and a read-only mount of those client certificates. This proved the scanner can inspect the image without weakening the isolation boundary.

## What did the first Trivy test find?

The first successful scan of `homelab-defender:6` showed two distinct results:

```text
Ubuntu packages:   0 HIGH / CRITICAL
usr/bin/pebble:    8 HIGH, 0 CRITICAL
```

The image therefore does not meet the planned HIGH/CRITICAL publication policy yet.

That is useful evidence, not a failed project. The security control did exactly what it is supposed to do: it found a problem before the image reached the private registry. The next step is to remediate or replace the vulnerable runtime component, rebuild the image and scan it again.

## Why the registry needs a username and password

The registry is a storage service for Docker images. It is not public and it should not accept images from unknown machines.

Jenkins has a dedicated account called `jenkins-ci`. Its password is stored in Jenkins Credentials, which keeps it out of:

- GitHub
- the `Jenkinsfile`
- build logs
- this documentation

The pipeline fetches the credential only while it is publishing, then logs out of the registry.

## What the Jenkins build choices mean

When starting a build manually, Jenkins offers two checkboxes:

| Option | What it does |
|---|---|
| `BUILD_CONTAINER` | Runs tests, packages the game and creates a Docker image inside Jenkins’ isolated builder. |
| `PUBLISH_CONTAINER` | Builds the image and, once the security gate is added and passes, continues to the authenticated private-registry publish stage. |

Publishing automatically includes building, so `PUBLISH_CONTAINER` requests the complete release path.

A build number gives each image a specific identity. For example, build 7 becomes:

```text
homelab-defender:7
```

Later, Kubernetes will be told to run that exact version rather than an ambiguous `latest` image.

## What happens next

The scanner connection and failure behaviour are now proven. The next steps are:

1. Remediate or replace the component responsible for the eight HIGH findings in `usr/bin/pebble`.
2. Rebuild `homelab-defender` from the updated runtime/base image.
3. Re-run Trivy and prove a compliant image can return a successful gate result.
4. Add a `Security Scan` stage between `Containerise` and `Publish image` in the `Jenkinsfile`.
5. Run the complete gated Jenkins pipeline.
6. Confirm only an image that passes the gate reaches the private registry.
7. Configure K3s to authenticate to the registry.
8. Create a separate Kubernetes test namespace and deploy a known image.

Only after the test deployment is dependable will we consider a Cloudflare-published game URL.

## Safety boundaries

- Jenkins remains internal-only.
- Jenkins does not receive TestServer's host Docker socket.
- Jenkins-to-builder Docker traffic uses TLS.
- The Trivy scanner is temporary and versioned rather than permanently added to Jenkins.
- The scanner receives only the access needed to inspect the isolated Docker builder.
- A failed security gate prevents the image publish stage from running.
- Cloudflare will publish the game, not Jenkins, Kubernetes control pages or the registry.
- Registry, Docker TLS and Kubernetes credentials stay out of GitHub.
- The first Kubernetes deployment is a test deployment, not a production service.
- Each release is identified by an immutable build number.

## Useful mental model

Think of it as a small workshop:

- **GitHub** is the design folder.
- **Gradle** is the build instruction sheet.
- **Jenkins** is the workbench that checks and assembles the product.
- **Docker** is the sealed delivery box.
- **Trivy** is the security inspector who checks the sealed box before it leaves the workshop.
- **The registry** is the locked stockroom.
- **The firewall rule** is the staff-only door between the workbench and stockroom.
- **Kubernetes** is the team that places an approved box into service.
- **Cloudflare** is the public reception desk.

The important change is that the box will not simply go from the workbench to the stockroom. It must pass the security inspector first.

The first real inspection has now taken place, found a problem, and correctly rejected the image. That is exactly the behaviour this delivery path is being built to provide.

Every hand-off is intentional, recorded and checked.
