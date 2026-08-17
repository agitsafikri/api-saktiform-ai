# PRD — Duplicate Order Filter (`isDouble`) on Order List

| Field | Value |
|---|---|
| Feature name | Duplicate Order Filter |
| Component | `GET /order` and `GET /order/export` → `OrderController` → `OrderService` → `OrderRepository` |
| Status | **Approved** — TDD tersedia: [TDD — Duplicate Order Filter](../tdd/order-double-filter.md) |
| Scope | Workspace-scoped (per tenant) |
| Author | — |
| Last updated | 2026-08-18 |

> All open questions raised during drafting have been answered by the product owner and are recorded verbatim under **§11 Resolved Decisions**. The sections below reflect those answers. **No code has been written yet** — this document is the input for `docs/tdd/order-double-filter.md`.

---

## 1. Background

`GET /order` returns a paginated, workspace-scoped list of orders. It already supports a set of independent, all-optional filters, each applied with `AND` semantics in a single native query (`OrderRepository.getOrderList`):

| Existing param | Type | Filters on |
|---|---|---|
| `workspaceId` (required) | `Long` | `produk.id_workspace` (tenant boundary) |
| `idProvinsi` / `idKota` / `idKecamatan` | `Integer` | order region |
| `status` | `OrderStatus` | `UNPAID` / `PAID` / `CANCELLED` |
| `jenisPembayaran` | `JenisPembayaran` | `COD` / `BANK_TRANSFER` |
| `statusEkspor` | `Boolean` | already-exported flag |
| `tanggalAwalOrder` / `tanggalAkhirOrder` | `LocalDateTime` | `order.created_at` |
| `tanggalAwalPaid` / `tanggalAkhirPaid` | `LocalDateTime` | `order.paid_at` |
| `search` | `String` | `ILIKE` over order code, product name, province, recipient name |
| `page` / `limit` | `Integer` | pagination (1-based input), sorted `created_at DESC` |

`GET /order/export` mirrors the same filter set (minus pagination) and returns an `.xlsx` file.

Relevant data facts:

- The recipient phone lives on `order.nomor_whatsapp` (`String`), and is exposed in the list response as `OrderListDto.getNomorWhatsapp()`.
- On write, the phone is normalized to Indonesian format (`62xxxxxxxxxx`) by `PhoneNumberUtil.normalizeToIndonesianFormat()` — applied in `OrderService.createOrder`, `createOrderOnChat`, `saveAbandonedOrder`, and on update via `OrderOrchestrationService`. Current-generation rows are therefore consistently formatted; **legacy rows predating that logic may not be** (see §10 / §12).
- There is no `Contact` FK on `Order` guaranteed to be populated for every order, so the phone string is the practical identity key for "same customer".

---

## 2. Problem Statement

Operations/CS teams need to spot **double orders** — the same customer (same WhatsApp number) appearing on more than one order. These arise from customers re-submitting the checkout form, ordering again before the first is processed, or deliberate abuse. Today the only way to find them is to eyeball the list or export to Excel and pivot manually, which does not scale and is error-prone.

There is currently no way to ask the API "show me only the orders whose WhatsApp number is shared with at least one other order."

---

## 3. Goals

- Add one optional query parameter, **`isDouble`** (`Boolean`), to `GET /order` and `GET /order/export`.
- When `isDouble=true`, return **only** orders whose `nomor_whatsapp` occurs on **2 or more** orders within the workspace.
- Keep the filter composable with every existing filter (`AND` semantics) and with pagination.
- Present duplicate results grouped so that orders sharing a number sit adjacent to each other.
- Preserve existing behavior exactly when `isDouble` is omitted, `null`, or `false`.

### Non-Goals

- Merging, auto-cancelling, blocking, or otherwise acting on duplicate orders — this is a **read/visibility** feature only.
- Deduplication at order-creation time (rejecting or warning on a duplicate submission).
- Duplicate detection on `AbandonedOrder` (`GET /order/abandoned`) — deferred (OQ-10).
- Duplicate detection across workspaces.
- Any change to the order list response shape — no `duplicateCount` field (OQ-6).
- Backfill or normalization of existing `order.nomor_whatsapp` values (OQ-4).
- Frontend work (a separate frontend PRD/TDD will follow if needed).

---

## 4. Scope

### Included

- New optional request param `isDouble` (`Boolean`) on **`GET /order`**.
- The same param on **`GET /order/export`**, so an operator can export exactly the duplicate set they are reviewing.
- Threading the param through `OrderController → OrderService → OrderRepository`, for both the data query and the `countQuery` (so pagination totals stay correct), and for the export query.
- Duplicate grouping keyed on `order.nomor_whatsapp` alone, scoped to the workspace, over all statuses and all time.
- Conditional result ordering when `isDouble=true` (group by phone, then `created_at DESC`).

### Not Included

- Schema changes. No new column or table is functionally required; a supporting index on `order (nomor_whatsapp)` may be added purely for performance (NFR-2).
- Data backfill of legacy phone formats.
- Response-shape changes to `OrderListDto` / `ExportOrderListDto`.
- `GET /order/abandoned`.

---

## 5. Functional Requirements

| ID | Requirement |
|---|---|
| FR-1 | `GET /order` MUST accept an optional `isDouble` query param of type `Boolean`. |
| FR-2 | When `isDouble` is omitted, `null`, **or `false`**, the endpoint MUST behave exactly as it does today — no duplicate filtering, no sort change. Only `true` activates the filter. |
| FR-3 | When `isDouble=true`, the result MUST contain only orders whose `nomor_whatsapp` is shared by **≥ 2** orders in the same workspace. Every member of a duplicate group is returned — not just the 2nd and later occurrences. |
| FR-4 | Duplicate grouping MUST be scoped to the workspace: an identical phone number in another workspace MUST NOT make an order count as double. |
| FR-5 | The duplicate population MUST be computed over **all** orders in the workspace, **independent** of the other filters applied in the same request. The other filters then narrow which of those duplicate orders are displayed. (See §7.) |
| FR-6 | Orders of **every** status — `UNPAID`, `PAID`, and `CANCELLED` — MUST count toward the duplicate group and MUST be eligible for display. |
| FR-7 | Duplicate detection MUST be **unbounded in time** — any two orders sharing a number qualify regardless of how far apart they were created. |
| FR-8 | The grouping key MUST be `nomor_whatsapp` **only**. Two orders from the same number for *different* products still count as double. |
| FR-9 | Duplicate counting MUST compare the number **as stored** in `order.nomor_whatsapp` (exact string equality). No normalization is performed at query time. |
| FR-10 | Orders with a `NULL` or blank `nomor_whatsapp` MUST NEVER be treated as duplicates of one another, and MUST be excluded from the `isDouble=true` result. |
| FR-11 | `isDouble` MUST compose with all existing filters using `AND` semantics. |
| FR-12 | Pagination MUST remain correct — `totalElements` / `totalPages` MUST reflect the duplicate-filtered result set, not the unfiltered one. |
| FR-13 | When `isDouble=true`, results MUST be ordered by `nomor_whatsapp`, then `created_at DESC`, so orders sharing a number appear adjacent and never split unpredictably across pages. When the filter is inactive, the existing `created_at DESC` ordering MUST be unchanged. |
| FR-14 | `GET /order/export` MUST accept the same `isDouble` param with identical semantics, and the exported `.xlsx` MUST contain exactly the rows the equivalent list request would return (ignoring pagination). |
| FR-15 | The response shape of `GET /order` MUST NOT change — no new fields are added to `OrderListDto`. |

---

## 6. Non-Functional Requirements

| ID | Requirement |
|---|---|
| NFR-1 | Consistent with the existing codebase style: the filter is added to the same native query using the established `(:param IS NULL OR …)` pattern, and to the matching `countQuery`. |
| NFR-2 | Duplicate detection requires evaluating, per row, whether another order in the workspace shares its phone. Response time for the order list MUST NOT degrade noticeably on realistic data volumes. Because no `duplicateCount` is exposed (FR-15), an `EXISTS` predicate is sufficient and cheaper than a `COUNT` aggregation. A supporting index on `order (nomor_whatsapp)` SHOULD be evaluated in the TDD. |
| NFR-3 | No regression to any existing filter, to sorting (`created_at DESC`), or to pagination when `isDouble` is not `true`. |
| NFR-4 | Multi-tenant isolation MUST be preserved — the duplicate lookup MUST join through `produk.id_workspace` exactly as the main query does. |
| NFR-5 | Backward compatible: existing clients that do not send `isDouble` see no change in behavior or response shape. Clients that send `isDouble=false` also see no change (FR-2). |

---

## 7. Behavior Definition — duplicate scope vs other filters

"Which orders are double?" has two defensible readings, and they diverge whenever another filter is also active. **Decision: Interpretation 1** (see OQ-1).

Sample data (one workspace):

| Order | Phone | Status |
|---|---|---|
| A | 628111 | PAID |
| B | 628111 | UNPAID |
| C | 628222 | PAID |
| D | 628333 | UNPAID |
| E | 628333 | UNPAID |

**✅ Interpretation 1 — global duplicate set (CHOSEN).** Duplicates are computed across all orders in the workspace; the other filters only decide which of those rows are shown.

- `isDouble=true` → A, B, D, E
- `isDouble=true&status=PAID` → **A** (A is double because of B, even though B is filtered out of the view)

**❌ Interpretation 2 — duplicate within the filtered set (rejected).** Filters applied first, duplicates detected among the survivors.

- `isDouble=true&status=PAID` → ∅ (within PAID orders, no phone repeats)

**Rationale.** The business question is "is this customer a repeat/duplicate?", which is a property of the customer, not of the current view. Interpretation 2 also makes results confusing — narrowing an unrelated filter would silently change whether a row counts as "double". Implementation-wise, Interpretation 1 is a self-contained `EXISTS` subquery against the workspace's orders, whereas Interpretation 2 would require the duplicate lookup to carry every filter parameter, roughly doubling query complexity.

---

## 8. API Contract

### `GET /order` — **Auth**

| Param | Type | Required | Default | Description |
|---|---|---|---|---|
| `isDouble` | `Boolean` | No | `null` | `true` → only orders whose WhatsApp number appears on ≥2 orders in the workspace, ordered grouped by number. `false` / omitted → no duplicate filtering (identical behavior). |

### `GET /order/export` — **Auth**

Same param, same semantics. Output remains a binary `.xlsx`.

Examples:
```
GET /order?workspaceId=1&page=1&limit=10&isDouble=true
GET /order?workspaceId=1&page=1&limit=10&isDouble=true&status=PAID
GET /order?workspaceId=1&page=1&limit=10&isDouble=false     → same as omitting it
GET /order/export?workspaceId=1&isDouble=true
```

Response: unchanged `RestResponse<Page<OrderListDto>>` envelope, unchanged row shape.

An invalid value (e.g. `isDouble=maybe`) follows existing framework behavior for `Boolean` binding failures — the controller's catch-all returns HTTP 400 with `success:false`.

---

## 9. Acceptance Criteria

Using the §7 sample data, single workspace:

- Given `isDouble` is omitted, when the list is requested, then all 5 orders are returned in `created_at DESC` order (today's behavior).
- Given `isDouble=false`, then the result is **identical** to omitting the param — all 5 orders, unchanged sort.
- Given `isDouble=true`, then exactly A, B, D, E are returned and C is not.
- Given `isDouble=true`, then `totalElements` is 4 (not 5).
- Given `isDouble=true`, then rows sharing a phone number are adjacent — e.g. A and B together, D and E together — ordered by `nomor_whatsapp` then `created_at DESC`.
- Given `isDouble=true&status=PAID`, then A is returned (Interpretation 1).
- Given a phone number with one `PAID` and one `CANCELLED` order, when `isDouble=true`, then both are returned (FR-6).
- Given two orders from the same number created 8 months apart, when `isDouble=true`, then both are returned (FR-7).
- Given two orders from the same number for **different products**, when `isDouble=true`, then both are returned (FR-8).
- Given an order in **another** workspace also has phone `628222`, when `isDouble=true` is requested for this workspace, then C is still **not** returned.
- Given two orders both have `nomor_whatsapp = NULL` (or `''`), when `isDouble=true`, then neither is returned.
- Given one order stores `08123456789` and another `628123456789`, when `isDouble=true`, then neither is returned — exact string matching only (FR-9, known limitation §12).
- Given a duplicate result spanning more than one page, when page 2 is requested, then paging is stable and no row is duplicated or skipped.
- Given `isDouble=true` combined with `search`, `tanggalAwalOrder`, and `idProvinsi`, then all filters apply together with `AND` semantics.
- Given `GET /order/export?isDouble=true`, then the `.xlsx` contains exactly the rows that `GET /order?isDouble=true` returns across all pages.

---

## 10. Edge Cases

- **Blank vs NULL phone.** Both are excluded from duplicate grouping (FR-10), otherwise every order with a missing phone would become "double" with every other.
- **Legacy un-normalized numbers.** Exact-string matching (FR-9) means `08123` and `628123` are treated as two different customers and neither is flagged. Accepted — see §12.
- **Same number, different product.** Counts as double (FR-8), per the change request as written.
- **Genuine repeat customers.** A loyal customer ordering months apart is flagged as double (FR-7). Users can narrow the view with the existing `tanggalAwalOrder` / `tanggalAkhirOrder` filters if this becomes noisy.
- **Cancelled counterpart.** A duplicate whose pair was already cancelled still shows as double (FR-6). Users can add `status` as a separate filter to exclude them.
- **`isDouble=false` from a checkbox.** A frontend checkbox that sends `false` when unchecked behaves correctly — `false` is a no-op (FR-2). This was the deciding factor for OQ-3.
- **Export side effect.** `OrderService.exportOrder()` calls `orderRepository.markAsExported(ids)`, so exporting a duplicate set flips those orders to `status_ekspor = true` even if the operator only meant to review them. This is **pre-existing behavior**, unchanged by this feature, and was explicitly accepted (OQ-5).
- **Sort switch is conditional.** Ordering changes only when `isDouble=true` (FR-13). A client that toggles the filter will see the list re-order — this is intended, not a bug.
- **Large workspaces.** The duplicate lookup touches the workspace's order set on every row; on high-volume tenants this is the main performance risk (NFR-2).

---

## 11. Resolved Decisions

All questions below were answered by the product owner (answers inline) and are reflected in the sections above.

| # | Question | Decision |
|---|---|---|
| **OQ-1** | **Duplicate scope vs other filters.** When `isDouble=true` is combined with another filter (e.g. `status=PAID`), should duplicates be computed over *all* workspace orders and then narrowed by the filters (Interpretation 1), or should the filters apply first and duplicates be detected only among survivors (Interpretation 2)? | **Interpretation 1** — duplicates computed across the whole workspace; other filters only narrow the display. → FR-5, §7 |
| **OQ-2** | **Do `CANCELLED` orders count?** Should a cancelled order still count toward its phone number's duplicate group, or be ignored for duplicate detection? | **Count them.** All statuses participate; users can exclude via the separate `status` filter. → FR-6 |
| **OQ-3** | **Meaning of `isDouble=false`.** Should `false` return only non-duplicate orders (the complement), or behave the same as omitting the param? | **Same as omitting.** Only `true` activates the filter — safe for frontend checkboxes that send `false` when unchecked. → FR-2 |
| **OQ-4** | **Phone matching strategy.** Match on the stored string exactly, normalize inside the query, or run a one-off backfill that normalizes existing `order.nomor_whatsapp` values? | **Exact match, no backfill.** Simplest and fastest; historical data is left untouched. False negatives on legacy rows accepted → §12. → FR-9 |
| **OQ-5** | **Apply to `GET /order/export` too?** | **Yes.** Filter sets stay in sync. The pre-existing `markAsExported` side effect is accepted. → FR-14 |
| **OQ-6** | **Expose `duplicateCount` in the response?** Should each row carry how many orders share its number, so the UI can badge it? | **No.** Response shape unchanged; the query can use a cheaper `EXISTS` instead of a `COUNT` aggregation. → FR-15, NFR-2 |
| **OQ-7** | **Sorting/grouping of duplicate results.** Keep global `created_at DESC`, or group orders sharing a number together? | **Group when `isDouble=true`** — order by `nomor_whatsapp`, then `created_at DESC`. Sorting is unchanged when the filter is inactive. → FR-13 |
| **OQ-8** | **Grouping key — phone only, or phone + product?** | **Phone only**, per the change request as written. Different products from the same number still count as double. → FR-8 |
| **OQ-9** | **Time window.** Unbounded, or limited to a window (e.g. same day / 7 days)? | **Unbounded.** Existing date filters let users narrow the view manually. → FR-7 |
| **OQ-10** | **Does the same filter belong on `GET /order/abandoned`?** | **Out of scope for now** — may be added later if requested. → §3 Non-Goals |

---

## 12. Known Limitations (accepted)

1. **Legacy phone formats produce false negatives.** Per OQ-4, matching is exact-string. Orders created before phone normalization was introduced (or imported with a different format) will not group with their normalized counterparts. New orders are unaffected because `create` and `update` both normalize. If duplicate detection later proves unreliable on historical data, the remedy is a one-off backfill — deliberately excluded from this scope.
2. **Repeat customers are indistinguishable from accidental doubles.** Per OQ-9, there is no time window. Expect legitimate repeat buyers in the results.
3. **Exporting a duplicate set marks those orders as exported.** Pre-existing behavior of `GET /order/export`, accepted under OQ-5.

---

## 13. Next Steps

1. ~~Write the TDD~~ — **done**: `docs/tdd/order-double-filter.md`. It settles the query strategy (`EXISTS` correlated subquery, chosen over `IN … HAVING COUNT(*) > 1` and over a window function), the `countQuery` change, how the conditional sort of FR-13 is applied on the list vs the non-paginated export, the index decision for `order (nomor_whatsapp)`, and the verification matrix derived from §9.
2. Implement across `OrderController`, `OrderService`, `OrderRepository` — see the phased plan in TDD §13.
3. Update `docs/api-reference.md` §6.6 with the new param on both `GET /order` and `GET /order/export`.
