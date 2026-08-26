# Homelab Defender — Public Publication Guide

## Purpose

This guide defines the safe path for publishing the Homelab Defender browser game externally and linking to it from the Engineering Portfolio.

The application delivery pipeline, Kubernetes deployment, Trivy security gate, rollback path and monitoring are already operational. Public publication must expose only the game workload. Jenkins, the private registry and Kubernetes control paths remain private.

## Accepted release baseline

The current validated release is Jenkins build 15:

```text
192.168.2.220:5000/homelab-defender:15@sha256:2154a1881acc63db852dbeebc7daf5890a1c9527c4b70837b2ad33fb76ad940b
```

Authoritative Kubernetes desired state is owned by:

```text
jrwroberts1976/kubernetes-homelab/applications/homelab-defender-test
```

Do not publish a different image merely to create the public route. Publication should begin from the already validated immutable release unless a newer release has completed the same validation and Git reconciliation.

## Security boundary

The public route may expose:

```text
Internet
  -> Cloudflare/public edge
  -> explicitly approved reverse-proxy/ingress route
  -> Homelab Defender application Service
```

It must not expose:

- Jenkins;
- TestServer private Docker registry on TCP 5000;
- K3s API/control-plane endpoints;
- SSH deployment identities or forced-command endpoints;
- Grafana administration;
- internal-only health/management endpoints beyond what is deliberately required for application routing.

## 1. Choose the public hostname

Choose and document one dedicated hostname under the existing public domain.

Example pattern only:

```text
https://<defender-hostname>.jrwroberts.co.uk/
```

Do not add the portfolio link until the hostname resolves externally and the application path has passed validation.

Record the final hostname in:

- the change record/daily actions;
- the portfolio build-time public URL variable; and
- any proxy/Cloudflare recovery documentation required for rebuilding the route.

## 2. Confirm application readiness before edge changes

Verify the currently approved release before introducing public routing:

- Deployment available replicas = desired replicas;
- pod `Running`;
- restart count acceptable;
- Service endpoint responds correctly;
- `/healthz` returns the expected healthy result;
- running image digest matches the Git-approved digest;
- Defender-specific Grafana alert expressions are healthy.

Stop if the private application path is not already healthy.

## 3. Create the narrow public route

Configure the public edge/reverse proxy so the dedicated hostname forwards only to the approved Homelab Defender application path.

Requirements:

- TLS at the public edge;
- no direct public exposure of Jenkins or registry ports;
- no wildcard forwarding into the homelab;
- no general-purpose Kubernetes API access;
- preserve the existing private registry and Jenkins firewall restrictions;
- use the smallest required ingress/proxy scope;
- retain a simple disable/remove procedure for the public route.

The exact implementation may use the existing Cloudflare/reverse-proxy pattern, but it must terminate at the Defender application Service rather than a management interface.

## 4. Validate the public game route

From outside the home network, verify:

1. HTTPS certificate is valid.
2. `/` loads the Defender game.
3. Static assets load successfully.
4. A complete game interaction works in a browser.
5. `/healthz` behaves as intended if it is deliberately public; otherwise verify health internally and do not expose it unnecessarily.
6. Unknown paths fail safely.
7. No Jenkins, registry or Kubernetes management endpoint is reachable through the hostname.
8. Application logs show only expected public traffic.
9. Defender monitoring remains healthy after external traffic begins.

## 5. Prepare the Engineering Portfolio link

The Engineering Portfolio should use a build-time public variable rather than hard-coding an internal address.

Planned variable:

```text
PUBLIC_HOMELAB_DEFENDER_URL=https://<final-public-hostname>/
```

The Jenkins Delivery/Homelab Defender project page should show a clear call to action such as:

```text
Play Homelab Defender
```

The button should:

- open the external game URL;
- use `target="_blank"` and `rel="noopener noreferrer"` where appropriate;
- only be rendered when `PUBLIC_HOMELAB_DEFENDER_URL` is non-empty;
- never fall back to an internal RFC1918 address or ClusterIP.

The link must not be enabled before external route validation passes.

## 6. Deploy and validate the portfolio change

Use the Engineering Portfolio's existing controlled production deployment process.

After deployment verify:

- portfolio health check passes;
- Jenkins Delivery project page renders correctly;
- `Play Homelab Defender` points to the validated public hostname;
- the link works from an external client;
- existing portfolio routes are unaffected.

## 7. Monitoring and rollback

Monitor both the application and public route after publication.

Rollback/publication-disable triggers include:

- game unavailable or materially broken;
- unexpected public exposure of a management endpoint;
- repeated application restarts;
- Defender deployment-unavailable alert;
- proxy/TLS misconfiguration;
- suspicious traffic that requires route withdrawal.

Fast rollback is to remove/disable the public route while leaving the already validated private Kubernetes deployment intact.

If the application release itself is faulty, use the existing Homelab Defender deployment rollback procedure rather than changing the public route to an unapproved image.

## Completion checklist

- [ ] Final public hostname chosen and documented.
- [ ] Build 15 or a newer fully validated immutable release confirmed healthy.
- [ ] Public route exposes only Homelab Defender.
- [ ] Jenkins remains private.
- [ ] Private registry remains private.
- [ ] Kubernetes control-plane access remains private.
- [ ] TLS validated externally.
- [ ] Browser/game smoke test passes externally.
- [ ] Monitoring healthy after public traffic begins.
- [ ] Portfolio `PUBLIC_HOMELAB_DEFENDER_URL` configured.
- [ ] `Play Homelab Defender` link deployed and externally tested.
- [ ] Public-route disable/rollback procedure recorded.
