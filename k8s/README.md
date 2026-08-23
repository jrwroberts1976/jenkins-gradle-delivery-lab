# Kubernetes deployment ownership

The Kubernetes desired state for the Homelab Defender test environment
is maintained in:

`jrwroberts1976/kubernetes-homelab/applications/homelab-defender-test`

This repository owns:

- application source and tests;
- the container image build;
- the Trivy image scan;
- publication to the private registry;
- the controlled Jenkins deployment trigger and node-side helper.

The `kubernetes-homelab` repository owns:

- the namespace;
- Deployment and Service definitions;
- the approved image tag and digest;
- the documented runtime desired state.

The current Jenkins deployment path uses the restricted
`jenkins-deploy` SSH account and the forced command
`deploy BUILD_NUMBER`. Until the deployment pipeline is converted to
update Git directly, successful Jenkins deployments must be reconciled
back into the Kubernetes desired-state repository.
