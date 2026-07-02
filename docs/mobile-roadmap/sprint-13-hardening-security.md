# Sprint 13 — Hardening: Security, Audit, Cluster-safety, Performance

**Phase:** 4 — Hardening · **Duration:** 2 weeks · **Goal:** make the
system production-grade — security, audit, DC cluster correctness,
performance, admin mobile settings.

> Spec: PDF §6.4 (security), §7 (dedupe), §8 (non-functional), §9 Phase 4,
> Appendix B (risks). Architecture §11 (security), §15 (ops).

## Objective

Close the non-functional gaps: enforce security invariants, add audit +
observability, guarantee cluster-safe push/reminders, hit performance
targets, add rate limiting, and give admins mobile settings control.
Optionally implement plugin-issued Mobile Session Tokens.

## In scope

- Security review of every mobile endpoint: positive + negative
  permission tests; no data-by-id-only (PDF §6.4, §9.1).
- `JimMobileAudit` AO for all sensitive writes (send, comment, reply,
  transition, assign, upload, mark-read, device register).
- Cluster-safety: dedupe push/reminders via fingerprints + cluster locks;
  AO-based dedup (PDF §7/§8, Appendix B).
- Performance: dashboard <2s, issue detail <3s; verify pagination/
  projection/caching; add server caching where safe (PDF §8).
- Rate limiting + abuse protection: login, message send, upload, search
  (PDF §6.4).
- Observability: structured logs + metrics (API latency, push success/
  failure, active devices, error rate) — PDF §8.
- Admin mobile settings surface (push settings, field-display rules mgmt,
  version/status, diagnostics) — PDF §1.1 admin persona, §4.8.
- **Optional:** `JimMobileSession` (short-lived access + refresh tokens)
  if the org chooses plugin-session auth over PAT (PDF §6.1).

## Backend track (plugin)

1. Add `JimMobileAudit` + write minimal records on every sensitive
   endpoint (userKey, action, entityType, entityId, ip, createdAt) —
   never log raw tokens (PDF §6.4).
2. Cluster-safe push: idempotency key/fingerprint per event; cluster lock
   or AO dedup so multi-node DC never double-sends.
3. Rate limiting (per-user/IP) on login/send/upload/search; consistent
   429 contract.
4. Metrics + structured logging hooks; extend the plugin diagnostics
   (`/admin/diagnostics`) with mobile health (active devices, push
   success rate).
5. Admin mobile settings endpoints (push config, field-rule CRUD, mobile
   feature flags).
6. (Optional) `JimMobileSession` issue/refresh/revoke endpoints + rotate
   on refresh; audit + revoke flow.

## Mobile track (Flutter)

1. Certificate pinning (optional per org — PDF §6.4); TLS-only.
2. Handle 429 with backoff + user-friendly messaging.
3. Enforce secure storage best-practices; scrub sensitive data from logs/
   crash reporting (PDF §9.1).
4. Performance passes: list virtualization, image caching, payload
   trimming, cold-start budget.
5. If session tokens adopted: refresh-token flow + silent re-auth.

## Verification (PDF §9 Phase 4 acceptance)

- Load test, security test, network-interruption test, and multi-node
  Jira DC test all pass.

## Design references

- `docs/states.md` — offline/stale, error/retry, rate-limit messaging.
- Architecture §11 (security), §13 (config), §15 (ops).

## Dependencies

- All feature sprints (audit/rate-limit/perf apply across endpoints).

## Acceptance criteria (PDF §9 Phase 4, §9.1)

- [ ] Every mobile endpoint has positive + negative permission tests.
- [ ] No sensitive data in logs, push payloads, or crash reports.
- [ ] No duplicate push/reminders on a multi-node cluster.
- [ ] Dashboard <2s, issue detail <3s under load.
- [ ] Rate limiting active on login/send/upload/search.
- [ ] Admin can manage mobile push settings + field rules.

## Definition of Done

- Consistent error + pagination contract across all endpoints (§9.1).
- Observability dashboards live (latency, push, devices, errors).
- Security review sign-off (see `review-security` skill for local diff
  review before release).
