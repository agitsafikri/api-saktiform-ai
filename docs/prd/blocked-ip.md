# PRD — Blocked IP (Manage + Enforcement)

| Field | Value |
|---|---|
| Feature name | Blocked IP |
| Component | New: `BlockedIp` entity/CRUD + request-level enforcement filter |
| Status | Implemented |
| Scope | Global (security applies to all workspaces) |
| Author | — |
| Last updated | 2026-06-29 |

> This PRD proposes a security feature to block specific IP addresses from using the application, plus CRUD to manage the block list. The open questions raised during drafting have been answered and are recorded under **Resolved Decisions**; the sections below reflect those answers.

---

## 1. Background

The application is a Spring Boot REST API sitting behind a reverse proxy (it is reached via a public host, and `server.forward-headers-strategy=framework` is configured). Security is JWT-based and stateless; the request pipeline runs a `JwtAuthenticationFilter` (a `OncePerRequestFilter`) registered **before** `UsernamePasswordAuthenticationFilter` in `SecurityConfig`.

The codebase already derives a caller's IP in one place — `OrderController.createOrder()` — using this precedence:

```
CF-Connecting-IP   (Cloudflare)
  → X-Forwarded-For (proxy)
    → request.getRemoteAddr() (direct)
```

There is currently **no** concept of blocking a caller by IP.

---

## 2. Problem Statement

The business needs a way to deny access to the application for specific IP addresses (e.g. abusive clients, attackers, scrapers). Operators need to manage this block list (add, view, update, remove entries), and the application must reject requests originating from a blocked IP before they are processed.

---

## 3. Goals

- Provide CRUD to manage a list of blocked IP addresses.
- Enforce the block list on incoming requests: a request from a blocked IP is rejected and cannot use the application.
- Make the block list global (it is a platform-level security control, not workspace-scoped).
- Reuse the existing IP-resolution precedence already used in the codebase, for consistency behind the proxy.

### Non-Goals
- Rate limiting, throttling, or automatic/dynamic blocking (this feature is manual block-list management only).
- Geo-blocking or ASN-based blocking.
- Per-workspace IP rules.
- Blocking by anything other than source IP (e.g. user-agent, account).

---

## 4. Scope

### Included
- A persisted `BlockedIp` record (IP address + timestamps only).
- Operations: create, list, delete (hard delete = unblock). **No update operation.**
- A request-level enforcement filter that rejects **all** requests from a blocked IP (global enforcement).
- IP resolution: `CF-Connecting-IP → X-Forwarded-For (first IP) → getRemoteAddr()`.
- Exact single-IP matching (IPv4 and IPv6).
- In-memory cache of the block list (TTL + refresh on change).
- A safety guard preventing an operator from blocking the server's own IP or the requester's current IP.

### Not Included
- File/firewall/network-layer blocking (this is application-layer only).
- CIDR ranges / wildcard matching (exact IP only).
- Automatic/dynamic blocking, rate limiting.
- Soft-disable toggle (unblock is a hard delete).
- Update/edit operation — to change an entry, delete it and create a new one.
- `reason` / `created_by` metadata or an action audit log (timestamps only).
- Migration tooling (schema is managed by Hibernate `ddl-auto=update`).

---

## 5. Functional Requirements

| ID | Requirement |
|---|---|
| FR-1 | An authenticated operator can create a blocked-IP entry (the IP address). |
| FR-2 | An authenticated operator can retrieve the list of blocked IPs (paginated, consistent with other list endpoints). |
| FR-3 | An authenticated operator can delete a blocked-IP entry (hard delete = unblock). |
| FR-4 | An incoming request whose resolved source IP exactly matches a blocked-IP entry MUST be rejected before reaching controller logic, for ALL endpoints (including public ones). |
| FR-5 | A rejected request MUST return HTTP **403 Forbidden** using the standard `RestResponse` envelope. |
| FR-6 | The block list is global — a blocked IP is denied across all workspaces and all endpoints. |
| FR-7 | Source IP MUST be resolved as: `CF-Connecting-IP`, then the **first** IP of `X-Forwarded-For`, then `getRemoteAddr()`. |
| FR-8 | Deleting an IP MUST restore that IP's access for subsequent requests (subject to cache refresh, NFR-1). |
| FR-9 | Exact-match only, supporting both IPv4 and IPv6 addresses. |
| FR-10 | `ip_address` MUST be unique; creating an already-blocked IP MUST be rejected. |
| FR-11 | Create MUST reject attempts to block the server's own IP or the requester's current resolved IP (self/server-lockout guard). |
| FR-12 | If the source IP cannot be resolved, the request MUST be denied (fail-closed). |

---

## 6. Non-Functional Requirements

| ID | Requirement |
|---|---|
| NFR-1 | Enforcement runs on every request; it MUST add negligible latency via an in-memory cache of blocked IPs (TTL-based expiry + refresh on create/update/delete). No per-request DB query. |
| NFR-2 | Management endpoints return the standard `RestResponse<T>` envelope and follow existing controller conventions. |
| NFR-3 | The enforcement filter MUST run early (before authentication) so blocked IPs are denied regardless of auth state. |
| NFR-4 | Enforcement is **fail-closed**: if the source IP cannot be resolved, the request is denied. ⚠️ Because enforcement is global, a fail-closed posture means a failure in block-list evaluation can deny all traffic — the cache must be robust (see Risks). |
| NFR-5 | Management endpoints are **authenticated** under `/master` (any valid JWT; no specific role). No `permitAll` change. |

---

## 7. New / Affected Components (conceptual)

| Layer | Element | Change |
|---|---|---|
| Entity | `BlockedIp` (new) | Table `blocked_ip`. |
| Repository | `BlockedIpRepository` (new) | Lookups: exists-by-ip, list. |
| Service | `BlockedIpService` (new) | Create / list / delete + provides the current blocked set to the filter (with caching per NFR-1). |
| Controller | `BlockedIpController` (new) | Create / list / delete endpoints (authenticated). |
| Filter | `BlockedIpFilter` (new, `OncePerRequestFilter`) | Resolves source IP, denies if blocked. Registered early in `SecurityConfig` (e.g. before `JwtAuthenticationFilter`). |
| Util | (reuse) IP-resolution logic | Mirror the `OrderController` precedence; consider extracting a shared helper to avoid duplication. |

---

## 8. Data Model (conceptual)

`blocked_ip` table (final):

| Field | Type | Notes |
|---|---|---|
| `id` | Long (auto-increment) | PK, consistent with other master tables. |
| `ip_address` | String | The blocked IP. **Unique** (IPv4 or IPv6). |
| `created_at` / `updated_at` | timestamp | Minimal audit only. |

No `reason`, `is_active`, or `created_by` columns (presence in the table = blocked; delete = unblock). Schema is added automatically by Hibernate `ddl-auto=update`. No other table changes.

---

## 9. API Requirements (high level)

All endpoints return `RestResponse<T>`, live under the **authenticated `/master`** group (any valid JWT, no specific role), and follow existing controller conventions.

| Capability | Endpoint (indicative) | Type | Notes |
|---|---|---|---|
| Create blocked IP | `POST /master/blocked-ip` | Write | Add an IP; rejects duplicates and self/server IP. |
| List blocked IPs | `GET /master/blocked-ip` | Read | Paginated list of entries. |
| Delete blocked IP | `DELETE/GET /master/blocked-ip/delete` | Write | Hard delete (unblock). |

The denial response for blocked callers is produced by the **filter** (403 + `RestResponse`), not a controller.

---

## 10. Enforcement Design (conceptual)

- A `OncePerRequestFilter` resolves the source IP (FR-7 precedence) and checks it against the cached block list.
- Source IP precedence: `CF-Connecting-IP` → first IP of `X-Forwarded-For` (split on `,`, take index 0, trim) → `getRemoteAddr()`.
- If the IP is blocked **or** cannot be resolved (fail-closed) → short-circuit with **403** + `RestResponse`; do not call the rest of the chain.
- If not blocked → continue normally.
- Scope: **all** requests, including public endpoints (webhooks, checkout, media). ⚠️ See Risks — blocking infrastructure IPs would break those flows.
- Filter ordering: placed **before** authentication so denial happens regardless of token state.
- Block-set source: an in-memory cache maintained by `BlockedIpService`, expired by TTL and refreshed on every create/update/delete (NFR-1).

---

## 11. Acceptance Criteria

- Given an IP is on the block list, when a request arrives from that IP (any endpoint), then it is rejected with **403** + `RestResponse` and the controller is never reached.
- Given an IP is not on the block list, when a request arrives from it, then it is processed normally.
- Given an operator creates a blocked-IP entry, when a subsequent request arrives from that IP, then it is blocked (after cache refresh, NFR-1).
- Given an operator deletes an entry, when a subsequent request arrives from that IP, then it is allowed again.
- Given the request passes through the proxy, when the source IP is resolved, then `CF-Connecting-IP → first X-Forwarded-For → getRemoteAddr()` is applied.
- Given the source IP cannot be resolved, when the request is evaluated, then it is denied (fail-closed).
- Given an operator tries to block the server's own IP or their own current IP, when they submit it, then the operation is rejected.
- Given an operator tries to add an IP that is already blocked, when they submit it, then the operation is rejected (uniqueness).
- Given an unauthenticated caller, when they call the `/master/blocked-ip` endpoints, then the request is rejected by the security filter.

---

## 12. Edge Cases

- **IP not resolvable** (all headers empty) — request is denied (fail-closed, FR-12).
- **`X-Forwarded-For` with multiple hops** — the first entry is treated as the client.
- **IPv6 vs IPv4** — both supported. Note the same logical address can have multiple textual forms; exact string match means a differently-formatted-but-equal IPv6 address would not match unless normalized (see Risks).
- **Self/server lockout** — blocking the server's own IP or the requester's current IP is rejected at create (FR-11).
- **Duplicate entries** — adding an already-blocked IP is rejected (FR-10, uniqueness).
- **Cache staleness** — a newly blocked IP may still get through until the cache refreshes (refresh-on-change mitigates this for single-instance deployments).

---

## 13. Resolved Decisions

Answers provided by the product owner, reflected in the sections above.

1. **Enforcement scope** — Global: ALL requests (including public endpoints).
2. **Client IP source** — `CF-Connecting-IP` → **first** IP of `X-Forwarded-For` → `getRemoteAddr()`.
3. **Match type** — Exact IP only (no CIDR/wildcard).
4. **Denial response** — HTTP **403** with the standard `RestResponse` envelope.
5. **Endpoint placement** — Under `/master`.
6. **Authorization** — Authenticated only (any valid JWT, no specific role). *(Reconciled: "no auth" was requested, but `/master` requires a token; per follow-up confirmation the endpoints stay authenticated rather than adding them to `permitAll`.)*
7. **State model** — Hard delete (no soft toggle); presence = blocked, delete = unblock. **No update operation** (to change an entry, delete and re-create).
8. **Cache** — In-memory, TTL expiry + refresh on create/update/delete.
9. **Fail mode** — Fail-closed (unresolved IP → deny).
10. **Uniqueness** — Required (unique `ip_address`).
11. **Audit** — Minimal: `created_at` / `updated_at` only.
12. **IPv4/IPv6** — Both supported.
13. **Safety** — Prevent blocking the server's own IP and the requester's current IP.

---

## 14. Risks (for awareness)

- **Global + fail-closed blast radius** — Since every request is gated and the posture is fail-closed, a defect in IP resolution or block-list evaluation could deny all traffic. The cache and resolution path must be robust and well-tested.
- **Blocking infrastructure** — Because enforcement covers public endpoints, blocking the WhatsApp webhook source, the checkout frontend, or the proxy's egress IP would break those flows. The self/server-IP guard (#13) reduces but does not eliminate this (e.g. third-party infra IPs are not protected).
- **Header spoofing** — `CF-Connecting-IP` / `X-Forwarded-For` are client-supplied; if the proxy does not strip/override them, a caller could spoof a non-blocked IP. Enforcement strength depends on the proxy sanitizing these headers.
- **IPv6 normalization** — Exact string matching may miss equivalent-but-differently-formatted IPv6 addresses unless addresses are normalized on store and compare.
- **Multi-instance cache** — If the API runs as multiple instances, an in-memory cache refreshed on local writes can be stale on other instances until TTL expiry.
