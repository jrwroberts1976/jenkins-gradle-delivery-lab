# Homelab Defender — Beginner’s Guide

This project is a safe place to learn how a small application moves from an idea in GitHub to a running service.

The application is **Homelab Defender**, a small browser game based on familiar operational problems such as failed backups, low disk space and security updates. The game is useful, but the main goal is learning the delivery process behind it.

## The simple picture

```text
Write code → test it → package it → put it in a container
→ store the container safely → run it on Kubernetes
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
| Private registry | A protected storage shelf for Docker packages | TestServer LAN |
| K3s / Kubernetes | The platform that will run the packaged game | k3s-node-01 |
| Cloudflare | The future public front door for the game | Internet edge |

## Why use all of this?

Without a delivery path, deploying software often means following a list of manual steps and hoping none are missed.

With this project:

1. A change is saved in GitHub.
2. Jenkins notices the change.
3. Jenkins runs the tests.
4. If the tests pass, Jenkins packages the application.
5. When explicitly approved, Jenkins creates a Docker image.
6. Jenkins puts that image in the private registry.
7. Kubernetes will later pull that exact image and run it in a test area.

That means we can trace a running version back to a specific Git commit and Jenkins build.

## Where we are today

We have completed the build side of the journey:

- The Java game and its Gradle build are in GitHub.
- Jenkins is running internally on TestServer.
- Jenkins has already proved it can run tests, create application packages and build a Docker image.
- Jenkins uses its own isolated Docker builder rather than controlling TestServer’s host Docker socket.
- A protected private registry already runs on the TestServer LAN.
- A dedicated `jenkins-ci` account exists for publishing images.
- TestServer Docker and Jenkins’ Docker builder are configured to use that registry.
- The Jenkins credential is stored in Jenkins, not in this repository.
- The first registry publish build is the next validation step.

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
| `PUBLISH_CONTAINER` | Does all of the above, then safely pushes an image tagged with the Jenkins build number to the private registry. |

Publishing automatically includes building, so only `PUBLISH_CONTAINER` is needed for the first registry test.

A build number makes the image immutable. For example, build 3 becomes:

```text
homelab-defender:3
```

Later, Kubernetes will be told to run that exact version rather than an ambiguous “latest” image.

## What happens next

The next task is deliberately small:

1. Run a Jenkins build with `PUBLISH_CONTAINER` selected.
2. Confirm the image appears in the private registry.
3. Configure K3s to authenticate to that registry.
4. Create a separate Kubernetes test namespace.
5. Deploy one known image, check its health endpoint, and prove rollback works.

Only after the test deployment is dependable will we consider a Cloudflare-published game URL.

## Safety boundaries

- Jenkins remains internal-only.
- Cloudflare will publish the game, not Jenkins, Kubernetes control pages or the registry.
- Registry and Kubernetes credentials stay out of GitHub.
- The first Kubernetes deployment is a test deployment, not a production service.
- Each release is identified by an immutable build number.

## Useful mental model

Think of it as a small workshop:

- **GitHub** is the design folder.
- **Gradle** is the build instruction sheet.
- **Jenkins** is the workbench that checks and assembles the product.
- **Docker** is the sealed delivery box.
- **The registry** is the locked stockroom.
- **Kubernetes** is the team that places the box into service.
- **Cloudflare** is the public reception desk.

Every hand-off is intentional, recorded and checked.
