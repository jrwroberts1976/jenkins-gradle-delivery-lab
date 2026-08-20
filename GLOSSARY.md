# Homelab Defender — Glossary

This is the living glossary for the Homelab Defender build and delivery lab. It explains the technologies, commands and delivery concepts used by the project in plain English.

**Maintenance rule:** whenever a new technology, command, security control or delivery concept becomes part of the project, add or update its entry here so the repository stays understandable without assuming prior knowledge.

| Term | Plain-English meaning | How it applies here |
|---|---|---|
| **Agent** | The machine or runtime Jenkins uses to execute pipeline work. | The pipeline currently uses `agent any`, so Jenkins chooses an available executor. |
| **Annotation (Kubernetes)** | Extra metadata attached to a Kubernetes object. | The deployment script writes `kubernetes.io/change-cause=Jenkins build <number>` so the live release can be traced back to Jenkins. |
| **Artifact** | A file produced by a build and kept for later use or evidence. | Gradle produces distribution ZIP/TAR files and Jenkins archives them. |
| **Artifact fingerprint** | A Jenkins record identifying an artifact by its contents. | The Package stage fingerprints archived distributions so a build can be traced to the exact files it produced. |
| **Automatic rollback** | Restoring the previous known-good release when a new deployment cannot be verified. | The K3s deployment script remembers the current image before updating and restores it if rollout or health verification fails. Build 13 proved this path. |
| **Base image** | An existing container image used as the starting point for another image. | The runtime stage uses `eclipse-temurin:21-jre-jammy`. |
| **Basic authentication** | A simple username/password authentication scheme used by HTTP services. | The private registry challenges clients for Basic authentication before allowing image access. |
| **bcrypt** | A password-hashing algorithm designed to store password verifiers securely rather than plaintext passwords. | The registry's `htpasswd` file uses bcrypt-compatible hashes for automation accounts. |
| **Bind mount** | A host or daemon path made available inside a container. | Mounts are used to give temporary containers access to required files or the isolated builder socket. |
| **Branch** | A named line of development in Git. | `main` is the primary branch built by Jenkins. |
| **Build** | One complete execution of the Jenkins pipeline. | Jenkins assigns each run a unique build number. |
| **Build number** | Jenkins' sequential identifier for a job run. | It becomes the release image tag, for example `homelab-defender:14`. |
| **BuildKit** | Docker's newer image-build engine. | Jenkins currently warns that the legacy builder is deprecated; moving to BuildKit is a future improvement. |
| **buildx** | Docker's command-line plugin for BuildKit and advanced builds. | Docker recommends it in the current Jenkins build output. |
| **Change cause** | Human-readable release information recorded on a Kubernetes Deployment revision. | The deployment script records the Jenkins build number as `kubernetes.io/change-cause`. |
| **CI/CD** | Continuous Integration / Continuous Delivery: automatically testing, packaging and delivering changes. | Jenkins now performs the complete gated path through Kubernetes deployment and health verification. |
| **Checkout** | Copying a particular Git revision into a build workspace. | Jenkins checks out the exact `main` commit before running the pipeline. |
| **Cloudflare** | An internet-edge service for DNS, proxying and public access. | It is planned to publish the game later; Jenkins and the registry remain internal-only. |
| **ClusterIP** | The default Kubernetes Service type, reachable only from inside the cluster. | Homelab Defender uses a ClusterIP so the application stays private during testing. |
| **Commit** | A saved, versioned change in Git. | Jenkins records the exact commit SHA and message used by each build. |
| **Configuration cache** | A Gradle optimisation that reuses build configuration between runs. | Jenkins logs show Gradle reusing the configuration cache on repeated builds. |
| **Container** | A running instance of a container image. | Jenkins, the isolated Docker builder, Trivy and the Homelab Defender workload all run in containers. |
| **Container image** | A packaged filesystem and startup definition used to create containers. | Jenkins builds `homelab-defender:<build-number>`. |
| **Containerise** | Package an application into a container image. | The Jenkins `Containerise` stage runs `docker build --pull ...`. |
| **containerd** | A container runtime that pulls, stores and runs container images. | K3s uses containerd `2.3.2-k3s2` to authenticate to the private registry and run Homelab Defender. |
| **Controller (Jenkins)** | The Jenkins service that manages jobs, pipelines and orchestration. | The controller runs internally on TestServer. |
| **CRI** | Container Runtime Interface: the Kubernetes API used to talk to a container runtime. | K3s talks to containerd through CRI; `crictl` was used to test that path directly. |
| **Credential ID** | The name Jenkins uses to refer to a protected credential without exposing its value. | `homelab-registry` identifies registry publishing credentials and `k3s-deploy-ssh` identifies the restricted deployment SSH key. |
| **crictl** | A command-line tool for inspecting and testing CRI-compatible container runtimes. | `sudo k3s crictl pull ...` proved containerd could authenticate to and pull the private image. |
| **CVE** | Common Vulnerabilities and Exposures: a public identifier for a known security vulnerability. | Trivy reports CVEs found in image components. |
| **Daemon** | A long-running background service. | `jenkins-docker` runs an isolated Docker daemon, while `docker-registry` runs as a host service on TestServer. |
| **Deployment (Kubernetes)** | A Kubernetes object that declares how many copies of an application should run and manages rolling updates. | The test Deployment runs one Homelab Defender pod in `homelab-defender-test`; Jenkins updates its image during a release. |
| **Deployment health verification** | A post-rollout check that confirms the released application is reachable through its intended service path. | After Kubernetes reports rollout success, the deployment script checks `/healthz` through the ClusterIP Service and retries up to 15 times. |
| **Digest** | A cryptographic identifier for the exact contents of a container image or manifest. | Docker reports SHA-256 digests after pushes and pulls, giving evidence of exact image content. Builds 12–14 produced the same digest because the application image contents were unchanged. |
| **Docker daemon** | The background Docker service that stores images and creates containers. | Jenkins talks to the isolated daemon in `jenkins-docker`, not TestServer's host Docker daemon. |
| **Docker-in-Docker (DinD)** | Running a Docker daemon inside a container. | `jenkins-docker` uses the `docker:dind` image to provide an isolated builder. |
| **Docker network** | A private virtual network connecting containers. | Jenkins and the builder share `homelab_apps`. |
| **Docker Registry HTTP API V2** | The standard HTTP API used by Docker clients to talk to a container registry. | Requests to `/v2/` and repository tag endpoints were used to verify connectivity, authentication and published images. |
| **Docker socket** | A Unix socket used to control a Docker daemon locally. | The isolated builder exposes `/var/run/docker.sock`; it belongs to that builder, not TestServer's host daemon. |
| **DOCKER_CERT_PATH** | Environment variable telling the Docker client where TLS client certificates are stored. | Jenkins uses `/certs/client`. |
| **DOCKER_HOST** | Environment variable telling the Docker client which Docker daemon to contact. | Jenkins uses `tcp://docker:2376`. |
| **DOCKER_TLS_VERIFY** | Environment variable telling Docker to verify the TLS certificate of the remote daemon. | It is set to `1` for the Jenkins-to-builder connection. |
| **Endpoint (registry)** | The network URL a container runtime uses to contact a registry. | K3s is explicitly configured to use `http://192.168.2.220:5000` because the internal registry does not use HTTPS. |
| **Exit code** | A number returned by a command to say whether it succeeded or failed. | Trivy returns non-zero when policy is breached; the deployment script also returns non-zero when rollout or health verification fails. |
| **Firewall rule** | A network rule that allows or blocks traffic. | Registry TCP 5000 is restricted to required Jenkins and K3s sources rather than opened to the whole LAN. |
| **Forced command (SSH)** | An SSH-key restriction that ignores arbitrary commands and always runs a predefined server-side command. | The `jenkins-deploy` key is forced through `/usr/local/sbin/jenkins-deploy-command`, preventing a normal interactive shell. |
| **Git** | Version-control software that records changes to files. | The project source, pipeline, Kubernetes baseline, deployment script and documentation are versioned in Git. |
| **GitHub** | The hosted repository containing the project source and history. | Jenkins fetches the public repository from GitHub. |
| **Gradle** | The Java build automation tool used by the project. | It compiles, tests and packages Homelab Defender. |
| **Gradle Wrapper** | Project files that select and download the required Gradle version. | Jenkins runs `./gradlew` so builds use the repository-defined Gradle version. |
| **HIGH / CRITICAL** | Vulnerability severity levels used by the security policy. | The Trivy gate blocks publication when findings at these levels are detected. |
| **hosts.toml** | containerd registry-host configuration describing which endpoints and capabilities to use for a registry. | K3s generated a `hosts.toml` entry containing the HTTP registry host after loading `registries.yaml`. |
| **HTTP 401 Unauthorized** | An HTTP response meaning the service was reached but supplied authentication was missing or rejected. | A registry `401` proved network reachability before authentication was supplied; valid credentials then returned HTTP 200. |
| **htpasswd** | A file format and command commonly used to store HTTP Basic-authentication usernames and hashed passwords. | TestServer's registry reads `/etc/docker/registry/htpasswd`; automation accounts are stored there as password hashes. |
| **Image pull** | Downloading a container image from a registry into a container runtime. | K3s/containerd first proved a manual pull with build 12 and later pulled build 14 as part of the automated release. |
| **imagePullPolicy** | Kubernetes rule controlling when the runtime should contact the registry for an image. | The Deployment uses `Always`, deliberately forcing registry contact for each rollout. |
| **Image tag** | A human-readable label attached to a container image. | Jenkins uses the build number, for example `homelab-defender:14`, instead of `latest`. |
| **Jammy** | Ubuntu 22.04 LTS, whose codename is Jammy Jellyfish. | The runtime base is `eclipse-temurin:21-jre-jammy`, selected after the previous runtime exposed HIGH findings in `usr/bin/pebble`. |
| **Java DB (Trivy)** | Trivy's vulnerability database used for Java-related analysis. | Trivy caches this database to avoid downloading it on every scan. |
| **JDK** | Java Development Kit: Java plus tools needed to compile software. | The Docker build stage uses a JDK-capable Gradle image. |
| **JRE** | Java Runtime Environment: the components needed to run Java software. | The final application image uses a smaller Java 21 runtime image. |
| **Jenkins** | Automation server used to run the delivery pipeline. | It now tests, packages, containerises, scans, publishes and deploys approved application releases. |
| **Jenkinsfile** | Pipeline-as-code file describing Jenkins stages and rules. | The repository's `Jenkinsfile` defines Test, Package, Containerise, Security Scan, Publish image and Deploy to K3s. |
| **JUnit** | A Java testing framework and report format understood by Jenkins. | Jenkins records test results after the pipeline run. |
| **K3s** | A lightweight Kubernetes distribution. | `k3s-node-01` runs K3s and hosts the Homelab Defender test deployment. |
| **Kubernetes** | A platform for running and managing containerised applications. | It currently runs the successfully released build 14 in the isolated `homelab-defender-test` namespace. |
| **Layer** | One filesystem change in a container image. | Docker builds images from layers and Trivy analyses their contents for vulnerabilities. |
| **Liveness probe** | A Kubernetes health check used to decide whether a running container is still alive and should be restarted if unhealthy. | The Homelab Defender Deployment checks `/healthz` on port 8080 after startup. |
| **Mirror (container registry)** | A registry endpoint containerd can use for requests to a named registry. | K3s `registries.yaml` defines the internal HTTP endpoint as the mirror for `192.168.2.220:5000`. |
| **Multibranch Pipeline** | A Jenkins job type that discovers branches containing a Jenkinsfile. | The project uses one and builds `main`. |
| **Namespace (Kubernetes)** | A logical boundary used to group and separate Kubernetes resources. | The application lives in `homelab-defender-test` rather than a system namespace. |
| **Pipeline** | An ordered set of automated delivery stages. | Current full-release flow: Test → Package → Containerise → Security Scan → Publish image → Deploy to K3s. |
| **Pipeline as Code** | Storing the pipeline definition in version control with the application. | The `Jenkinsfile` is committed to GitHub. |
| **Pod** | Kubernetes' smallest deployable unit, usually containing one application container. | The Deployment creates the Homelab Defender pod and build 14 is the first fully automated successful release. |
| **Private registry** | A server used to store container images that is not public. | Approved Homelab Defender images are pushed to the authenticated LAN registry and pulled by K3s. |
| **Publish** | Make an approved image available in the registry for deployment. | The Publish image stage runs only when `PUBLISH_CONTAINER` is selected and all earlier stages, including Trivy, succeed. |
| **Readiness probe** | A Kubernetes health check used to decide whether a pod is ready to receive traffic. | The Homelab Defender Deployment checks `/healthz` on port 8080 before the Service should send traffic to the pod. |
| **Registry credential** | Username/password used to authenticate to the private container registry. | Jenkins stores its publishing credential under `homelab-registry`; K3s stores a separate pull credential in root-owned registry configuration. |
| **registries.yaml** | K3s configuration file for private registry endpoints, authentication and related settings. | `/etc/rancher/k3s/registries.yaml` defines the internal HTTP endpoint and K3s pull credentials. K3s must be restarted after changes so containerd configuration is regenerated. |
| **Registry fallback** | containerd's attempt to contact the default registry endpoint when configured endpoints do not complete the request. | Before K3s loaded `registries.yaml`, the pull fell back to HTTPS and failed because the internal registry serves HTTP. |
| **Release** | A build that has passed its required controls and is deliberately put into service. | With `PUBLISH_CONTAINER=true`, the Jenkins build number identifies the registry image and the Kubernetes release request. |
| **Rollback** | Returning an application to a previous known-good version. | The automated script restores the previously running image after a failed rollout or persistent health failure; Kubernetes `rollout undo` is also documented for manual recovery. |
| **Rollout** | Kubernetes' process of replacing old pods with pods using a new Deployment template. | Jenkins waits for `kubectl rollout status` before performing the Service-level health check. |
| **Runtime image** | The final image containing only what is needed to run the application. | The project uses `eclipse-temurin:21-jre-jammy` for the final stage. |
| **Security gate** | An automated check that must pass before delivery is allowed to continue. | Trivy scans HIGH/CRITICAL vulnerabilities and a failing result prevents Publish image from running. |
| **Security Scan stage** | The Jenkins stage that performs the vulnerability gate. | It runs pinned Trivy `0.72.0` against the image just built by Jenkins. |
| **Service (Kubernetes)** | A stable Kubernetes network endpoint that routes traffic to matching pods. | A ClusterIP Service exposes Homelab Defender internally on port 8080 and is used by post-deployment verification. |
| **Service account (general)** | A non-human account intended for an application or automation process. | `jenkins-ci` publishes images, K3s has a separate registry pull identity, and `jenkins-deploy` is used only for deployment requests. |
| **SHA / commit SHA** | Cryptographic identifier for a Git commit. | Jenkins logs the exact SHA it checked out, making builds traceable to source. |
| **SSH key credential** | A private/public key pair used for non-password SSH authentication. | Jenkins stores the deployment private key under credential ID `k3s-deploy-ssh`; the matching public key on K3s is constrained by a forced command. |
| **Stage** | A named section of a Jenkins pipeline. | Examples are Test, Package, Containerise, Security Scan, Publish image and Deploy to K3s. |
| **sudo rule** | A policy controlling which commands one local account may run as another user, usually root. | `jenkins-deploy` has a narrow NOPASSWD rule allowing only `/usr/local/sbin/deploy-homelab-defender`. |
| **Temurin** | Eclipse Adoptium's OpenJDK distribution. | The final runtime is based on `eclipse-temurin:21-jre-jammy`. |
| **TLS** | Transport Layer Security: encryption plus identity verification for network connections. | Jenkins connects securely to the isolated Docker daemon on port 2376 using client certificates. The private registry itself currently uses LAN HTTP with authentication and firewall restrictions. |
| **Trivy** | Vulnerability and misconfiguration scanner from Aqua Security. | Version `0.72.0` is pinned in the Jenkins Security Scan stage. |
| **Vulnerability database** | Structured data mapping software versions to known security problems. | Trivy downloads/caches its databases and compares image contents against them. |
| **Workspace** | The directory Jenkins uses for a particular job's checked-out files and build output. | The `main` job runs inside its Jenkins workspace before artifacts and images are produced. |

## Project-specific shorthand

| Name | Meaning |
|---|---|
| **TestServer** | Host running Jenkins, the private registry and other homelab services. |
| **jenkins** | The Jenkins controller container. |
| **jenkins-docker** | The isolated Docker-in-Docker builder used by Jenkins. |
| **docker-registry** | Host service on TestServer listening on port `5000` and serving the authenticated private Docker registry. |
| **homelab_apps** | Docker network shared by Jenkins and the isolated builder. |
| **homelab-defender** | Application and container-image name for this project. |
| **homelab-defender-test** | Kubernetes namespace used for the isolated deployment. |
| **jenkins-ci** | Dedicated non-human registry account used by Jenkins for image publishing. |
| **jenkins-deploy** | Dedicated restricted SSH account on `k3s-node-01` used only to request Homelab Defender deployments. |
| **k3s-deploy-ssh** | Jenkins credential ID containing the restricted SSH private key used by the Deploy to K3s stage. |
| **k3s-node-01** | K3s control-plane node currently running the Homelab Defender test workload. |
| **`BUILD_CONTAINER`** | Jenkins parameter that asks the pipeline to build and scan a container image without publishing or deploying it. |
| **`PUBLISH_CONTAINER`** | Jenkins parameter that requests the full gated release path: build, scan, publish, deploy, rollout wait and health verification. |
| **`ops/deploy-homelab-defender`** | Version-controlled source of the root-owned K3s deployment and automatic rollback script. |

## Updating this glossary

Keep entries short, practical and tied to the project. Add a term when it first becomes part of the implementation or documentation. Update an existing entry when the design changes rather than leaving obsolete descriptions behind.
