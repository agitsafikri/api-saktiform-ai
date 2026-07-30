# PRD — Blocked Provinces

| Field | Value |
|---|---|
| Feature name | Blocked Provinces |
| Status | Implemented |
| Scope | Global (affects all workspaces) |
| Author | — |
| Last updated | 2026-06-29 |

> This PRD originally captured open questions during drafting; those have since been answered by the product owner and the feature has been implemented. The answers are recorded under **Resolved Decisions**, and the requirement sections below reflect the implemented behavior.

---

## 1. Background

The platform stores Indonesian geographic reference data in three global tables: `province`, `city`, and `district`. These are **not** workspace-scoped — they are shared reference data used across all workspaces.

Provinces are currently exposed through a read API (`GET /location/province`) that is consumed during flows such as address selection and checkout. The `province` table today contains only `province_id` and `province_name`; there is no concept of a province being unavailable.

The business now needs the ability to **block** specific provinces so they stop appearing in province lookups, while leaving all existing/historical data untouched.

---

## 2. Problem Statement

There is currently no way to remove a province from selection without deleting reference data. Operators need a controlled, reversible way to make a province unavailable for **future** reads, applied **globally** across every workspace, **without** altering or migrating any existing transaction data.

---

## 3. Goals

- Introduce a global "blocked" state for a province.
- Exclude blocked provinces from the province list API for all future reads.
- Allow an authorized operator to block, unblock, and list blocked provinces.
- Guarantee no change to existing transaction/historical data.
- Stay consistent with existing project conventions (`RestResponse<T>` envelope, REST endpoint style, JPA repository layer).

### Non-Goals
- Per-workspace province blocking.
- Any back-fill, migration, or rewrite of historical orders/addresses.
- Independent blocking at the city or district level. (City/district visibility is **derived** from their parent province's blocked state — there is no separate city/district block flag.)

---

## 4. Scope

### Included
- A new global flag on the `province` reference data indicating blocked/unblocked.
- Excluding blocked provinces from the province list read API.
- Admin capability to mark a province blocked.
- Admin capability to mark a province unblocked.
- Admin capability to retrieve the list of blocked provinces.

### Included (cont.)
- Cascading read-filter: cities and districts of a blocked province are excluded from their list APIs (derived from province state, no new columns).

### Not Included
- Changing, migrating, or recalculating any existing `order`, `abandon_order`, or address data.
- Separate/independent blocking of individual cities or districts.
- Blocking behavior in write paths such as order creation / checkout submission (writes are unaffected).
- Per-workspace overrides or workspace-level configuration.
- UI/frontend design (this PRD covers backend behavior only).

---

## 5. Functional Requirements

| ID | Requirement |
|---|---|
| FR-1 | A province can carry a global boolean state representing whether it is blocked. |
| FR-2 | When a province is blocked, it MUST NOT appear in the province list read API. |
| FR-3 | When a province is unblocked, it MUST appear in the province list read API (subject to existing behavior). |
| FR-4 | An authorized operator can mark a specific province as blocked. |
| FR-5 | An authorized operator can mark a specific province as unblocked. |
| FR-6 | An authorized operator can retrieve the list of currently blocked provinces. |
| FR-7 | The blocked state is global and applies identically to every workspace. |
| FR-8 | Blocking or unblocking a province MUST NOT modify any existing transaction or address records that reference that province. |
| FR-9 | Only read (GET) responses for the province list are affected; existing stored data is read back unchanged where it already exists. |
| FR-10 | When a province is blocked, its cities MUST NOT appear in the city list API, and its districts MUST NOT appear in the district list API (cascading exclusion derived from province state). |
| FR-11 | Unblocking a province MUST automatically restore its cities and districts to their list APIs (no separate action required). |

---

## 6. Non-Functional Requirements

| ID | Requirement |
|---|---|
| NFR-1 | Responses follow the existing `RestResponse<T>` envelope convention. |
| NFR-2 | Endpoints follow existing RESTful naming/structure conventions used in the project. |
| NFR-3 | The province list read performance MUST NOT materially degrade after adding the filter. |
| NFR-4 | Block/unblock/list-blocked operations live under the authenticated `/master` group (NOT the public `/location` group). They require a valid JWT but no specific role — any authenticated user may call them. |
| NFR-5 | The change MUST be backward compatible: existing consumers of the province list continue to work, simply receiving fewer entries when provinces are blocked. |
| NFR-6 | Operations should be idempotent where applicable (blocking an already-blocked province yields the same end state). |

---

## 7. API Requirements (high level only)

All endpoints return the standard `RestResponse<T>` envelope. The existing province read stays on the public `/location` group; the new admin operations live on the authenticated `/master` group (the public `GET /location/province` already excludes blocked provinces).

| Capability | Endpoint | Type | Notes |
|---|---|---|---|
| List provinces (existing) | `GET /location/province` | Read | Public. MUST exclude blocked provinces. |
| List cities (existing) | `GET /location/city` | Read | Public. MUST exclude cities of blocked provinces. |
| List districts (existing) | `GET /location/district` | Read | Public. MUST exclude districts of blocked provinces. |
| List blocked provinces | `GET /master/province/blocked` | Read | Authenticated. Returns the blocked subset only. |
| Block provinces | `POST /master/province/block` | Write | Authenticated. Accepts a list of province ids (bulk). |
| Unblock provinces | `POST /master/province/unblock` | Write | Authenticated. Accepts a list of province ids (bulk). |

**Design notes:**
- Block/unblock accept a **list** of province ids in the request body (bulk operation).
- If any province id in the request does not exist, the operation returns **404 Not Found** and no changes are applied.
- Admin operations sit under `/master` so they require authentication; the public province read remains under `/location`. No specific role is enforced.

---

## 8. Data Model Changes (conceptual only)

- Add a single **boolean** attribute named **`is_disabled`** to the `province` reference table representing the blocked state.
- Semantics: `true` = blocked (excluded from province list reads); `false` = not blocked (normal behavior).
- Constraints: **NOT NULL**, **DEFAULT FALSE** — existing rows are back-filled to `false` (not blocked).
- The attribute is **global** — it lives on the shared `province` row, not on any workspace-scoped table.
- No other tables change. No foreign keys, no new tables, no relationships added.
- No changes to `order`, `abandon_order`, `city`, `district`, or address-related data.

---

## 9. User Stories

- **US-1** — As an operator, I want to block a province so that it no longer appears for selection across all workspaces, without deleting its reference data.
- **US-2** — As an operator, I want to unblock a previously blocked province so it becomes selectable again.
- **US-3** — As an operator, I want to see the list of currently blocked provinces so I can audit and manage them.
- **US-4** — As an end user, when I read the province list, I should only see provinces that are not blocked.
- **US-5** — As a business stakeholder, I want existing orders/addresses tied to a (now blocked) province to remain intact and viewable.

---

## 10. Acceptance Criteria

**Province list read (FR-2, FR-3)**
- Given a province with blocked = true, when the province list API is called, then that province is absent from the response.
- Given a province with blocked = false, when the province list API is called, then that province is present in the response.

**Block (FR-4)**
- Given a list of existing provinces that are not blocked, when they are blocked, then their state becomes blocked and they are excluded from subsequent province list reads.

**Unblock (FR-5)**
- Given a list of blocked provinces, when they are unblocked, then their state becomes not blocked and they reappear in subsequent province list reads.

**List blocked (FR-6)**
- Given one or more blocked provinces, when the blocked list is requested, then exactly those provinces (the blocked subset only) are returned.

**Cascade to city/district (FR-10, FR-11)**
- Given a blocked province, when the city list API is called, then cities belonging to that province are absent from the response.
- Given a blocked province, when the district list API is called, then districts belonging to that province (via their city) are absent from the response.
- Given a province that is unblocked again, when the city/district list APIs are called, then its cities and districts reappear without any further action.

**Unknown province id**
- Given a block/unblock request where any province id does not exist, then the request returns **404 Not Found** and no province state is changed.

**Data integrity (FR-8, FR-9)**
- Given an existing order/address referencing a province, when that province is blocked, then the existing order/address record is unchanged and still readable.

**Access (NFR-4)**
- Given an authenticated caller (valid JWT, any role), when they call block/unblock/list-blocked under `/master`, then the request is processed.
- Given an unauthenticated caller, when they call those `/master` endpoints, then the request is rejected by the security filter (consistent with other `/master` endpoints).

**Global scope (FR-7)**
- Given a province blocked once, when the province list is read from any workspace context, then the province is excluded for all of them.

---

## 11. Edge Cases

- Blocking a province that is already blocked (expected: no error, state unchanged — idempotent).
- Unblocking a province that is already unblocked (expected: no error, state unchanged — idempotent).
- Targeting a non-existent province id for block/unblock → returns **404 Not Found**, no changes applied.
- All provinces blocked → province list returns an empty (but valid) list.
- A blocked province is still referenced by historical orders/addresses (must remain valid and readable).
- A new order/checkout attempt that targets a blocked province is **not** rejected — the feature affects read functions only; write paths are unchanged.
- Concurrent block and unblock of the same province (last-writer behavior / consistency TBD).
- Cached province lists on any consumer side may briefly serve stale data after a state change (cache strategy TBD).

---

## 12. Resolved Decisions

All questions raised during drafting have been answered by the product owner (answers inline below) and reflected in the requirement sections above.

1. **Authorization role** — Which role(s) may block/unblock/list blocked provinces? The project's roles are `OWNER`, `CUSTOMER_SERVICE`, `ADMIN`. Is this `ADMIN`/`OWNER` only, or restricted to a superadmin-type actor? Answer: No role-based restriction. The admin operations live under the authenticated `/master` group, so they require a valid JWT but no specific role.
2. **Endpoint placement & security** — The existing province read sits under the public `/location/**` group. Where should the new admin endpoints live so they are authenticated, while the public read continues to work? Should they be grouped under an admin/master path? Answer: Yes — the block/unblock/list-blocked endpoints were moved to `/master` (authenticated), because `/location` is public. The public province read stays on `/location`.
3. **Affected read endpoints** — Does "province list API" mean only `GET /location/province`? Are there other places that list/return provinces (e.g., dropdowns, checkout-related reads, dashboards) that must also exclude blocked provinces? Answer: yes
4. **Cascade to city/district** — If a province is blocked, should its cities (`GET /location/city`) and districts (`GET /location/district`) also be hidden, or are those out of scope? Answer (updated): Yes — cities and districts of a blocked province must also be hidden. Implemented as a **derived read-filter** (no `is_disabled` columns on city/district): the city and district list queries exclude rows whose parent province is blocked (district → city → province). Unblocking a province automatically restores its cities and districts. No data migration; only reads are affected.
5. **Write-path enforcement** — Should creating an order / submitting checkout for a blocked province be rejected, or is the feature strictly read-filtering? The rules state "only future reads are affected," which implies writes are NOT blocked — please confirm explicitly, since the checkout and order-create endpoints are public. Answer: only affect read function
6. **Default value & nullability** — For existing rows, what is the default for the new flag (presumably "not blocked"), and should the column be nullable or non-null with a default? (Not assumed here.) Answer: disabled = false, not null
7. **Single vs. bulk operations** — Should block/unblock support multiple provinces in one request, or strictly one province at a time? Answer: can bulk by adding list as payload
8. **Behavior on unknown province id** — Should block/unblock of a non-existent province return an error, or succeed silently? Answer: return 404 not found
9. **Auditability** — Is any audit trail required (who blocked/unblocked a province and when)? The project has an `order_history` pattern but no general audit log; is one expected here? Answer: no, only updated at needed
10. **Visibility of blocked list** — Should the "list blocked provinces" response include unblocked provinces with a flag, or only the blocked subset? Answer: only blocked list
11. **Naming** — The requested column name is `disabled`, while the feature/domain term is "blocked." Should the persisted column, the API field, and the documentation all use one consistent term to avoid ambiguity? Answer: just use isDisabled
12. **Idempotency contract** — Confirm that repeated block/unblock calls should be idempotent (this PRD assumes the desired behavior but flags it for explicit confirmation). Answer: yes