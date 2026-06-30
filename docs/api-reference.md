# Saktiform API Reference

> Audience: a frontend developer / AI coding agent integrating with this backend.
> This document describes every HTTP endpoint, its auth requirement, request shape, and response shape. Read the **Conventions** section first — it applies to every endpoint.

---

## 1. Overview

- **Stack:** Spring Boot REST API (multi-tenant conversational-commerce platform).
- **Default base URL (local):** `http://localhost:8081` (server port `8081`, no context path). In other environments the host differs; paths are identical.
- **Content type:** `application/json` for request/response bodies (except file upload = `multipart/form-data`, and order export = binary `.xlsx`).
- **Multi-tenancy:** Almost every data endpoint is scoped to a **workspace**. Most list/read endpoints require a `workspaceId` query param. There is no implicit tenant context — you must pass `workspaceId` explicitly.

---

## 2. Authentication

JWT, stateless. Obtain a token via login, then send it on every authenticated request.

### Login
`POST /account/login` — **Public**

Request body (`LoginRequest`):
```json
{ "username": "string", "password": "string" }
```

Response `data` (`LoginResponse`):
```json
{
  "token": "<JWT>",
  "username": "string",
  "nama": "string",
  "role": "OWNER | CUSTOMER_SERVICE | ADMIN",
  "workspaces": [ { "id": 1, "namaWorkspace": "string" } ]
}
```

### Using the token
Send the token as a Bearer header on every **Auth** endpoint:
```
Authorization: Bearer <JWT>
```
- Algorithm HS256; **expires after 24 hours**. On expiry, re-login.
- The token subject is the `username`. Role is `OWNER`, `CUSTOMER_SERVICE`, or `ADMIN`.
- No role-based gating currently: any valid token may call any **Auth** endpoint.

### Public endpoints (no token required)
`/account/login`, `/order/create`, `/produk/checkout`, `/location/**`, `/whatsapp/webhook` (+ `/whatsapp/*/webhook`), `/media/**`, `/uploads/**`, `/files/**`, `/swagger-ui/**`, `/v3/api-docs/**`, `/ws/**`. **Everything else requires a token.**

### IP blocking (applies to ALL requests)
A global filter rejects requests from blocked IPs **before** auth — including public endpoints. A blocked (or unresolvable) client IP receives:
```
HTTP 403 Forbidden
{ "success": false, "message": "Akses ditolak", "data": null }
```
Client IP is resolved as `CF-Connecting-IP` → first `X-Forwarded-For` → remote address.

---

## 3. Response Envelope

**Every** JSON endpoint returns this wrapper (`RestResponse`):
```json
{ "success": true, "message": "Success", "data": <payload | null> }
```
- `success` (boolean), `message` (string), `data` (object/array/null — varies per endpoint).
- On error, typically: `{ "success": false, "message": "<reason>", "data": null }` with HTTP 400 (or 403 for blocked IP, 404 where noted).
- **Exceptions to the envelope:** `GET /order/export` returns a binary `.xlsx` file; file-upload endpoints return the URL string inside `data`.

### Validation errors
Endpoints with `@Valid` bodies (e.g. `POST /order/create`, `POST /order/update`) return `data` as a list of field errors when validation fails, with `message: "Error Validasi"` and HTTP 400.

---

## 4. Pagination

List endpoints accept `page` (default `1`, 1-based) and `limit` (default `10`) query params and return a Spring `Page` object in `data`:
```json
{
  "content": [ /* rows */ ],
  "totalElements": 42,
  "totalPages": 5,
  "number": 0,            // current page index (0-based)
  "size": 10,
  "first": true,
  "last": false,
  "numberOfElements": 10
}
```
> Note: request `page` is **1-based**, but the returned `number` is **0-based** (Spring convention).

---

## 5. Enums

| Enum | Values |
|---|---|
| `Role` | `OWNER`, `CUSTOMER_SERVICE`, `ADMIN` |
| `OrderStatus` | `UNPAID`, `PAID`, `CANCELLED` |
| `JenisPembayaran` | `COD`, `BANK_TRANSFER` |
| `ChatType` | message type for `/send-message` (e.g. text/media) |
| `ChatStatus` | conversation chat status (see `GET /chat/status`) |

Date params use the format **`yyyy-MM-dd HH:mm:ss`** (order filters) or **`yyyy-MM-dd HH:mm`** (chat conversation filters) unless otherwise noted.

---

## 6. Endpoints

### 6.1 Account — `/account`

| Method | Path | Auth | Params / Body | Response `data` |
|---|---|---|---|---|
| POST | `/account/login` | Public | Body `LoginRequest {username, password}` | `LoginResponse` (see §2) |
| POST | `/account` | **Auth** | Body `RegisterRequest {id?, nama, username, password, role, idWorkspaces:[Long]}` | `null` |
| GET | `/account` | Auth | `page`, `limit`, `search?` | Page of `AccountListDto {id, nama, role, username, workspaces}` |
| GET | `/account/{id}` | Auth | path `id:Long` | `DetailAccountDto {id, nama, username, role, workspaces:[WorkspaceAccount]}` |
| GET | `/account/role` | Auth | — | List of role names |
| GET | `/account/list` | Auth | — | List of `AccountDropdownDto {id, username, name}` |
| POST | `/account/delete` | Auth | Body `DeleteAccountDto {id:Long}` | `null` |
| POST | `/account/reset-password` | Auth | Body `ResetPasswordDto {id:Long, newPassword}` | `null` |

> Note: `POST /account` (register) **requires a token** — only `/account/login` is public.

### 6.2 Workspace — `/workspace`

| Method | Path | Auth | Params / Body | Response `data` |
|---|---|---|---|---|
| POST | `/workspace` | Auth | Body `AddWorkspaceDto {namaWorkspace, wabaId:UUID, idUsers:[Long], gudang:GudangDto}` | `null` |
| POST | `/workspace/update` | Auth | Body `UpdateWorkspaceDto {id:Long, namaWorkspace, wabaId:UUID}` | `null` |
| GET | `/workspace` | Auth | `page`, `limit`, `search?` | Page of `WorkspaceListDto {idWorkspace, namaWorkspace, totalUser, nomorWaba, statusWaba}` |
| GET | `/workspace/{id}` | Auth | path `id:Long` | `DetailWorkspace {id, namaWorkspace, wabaId, users:[AccountDropdownDto], gudang:GudangDto}` |
| GET | `/workspace/list` | Auth | — | List of `WorkspaceDropdownDto {id, namaWorkspace}` |
| GET | `/workspace/{id}/account` | Auth | path `id:Long`; `page`, `limit` | Page of `WorkspaceAccountList {id, username, role, name}` |
| GET | `/workspace/{id}/account/remove` | Auth | path `id:Long`; `idAccount:Long` | `null` |
| POST | `/workspace/{id}/account` | Auth | path `id:Long`; Body `AddListAccountToWorkspace {accountId:[Long]}` | workspace + account data |
| GET | `/workspace/{id}/domain` | Auth | path `id:Long` | List of `DomainDto {id, domain}` |
| POST | `/workspace/domain` | Auth | Body `SetDomainToWorkspaceRequest {idWorkspace:Long, idDomain:Long}` | `null` |
| GET | `/workspace/{id}/dashboard-matrix` | Auth | path `id:Long`; `startDate?`, `endDate?` (`yyyy-MM-dd HH:mm:ss`) | `WorkspaceDashboardMatrix {totalOrder, totalBayar, rasioBayar, unpaidOrder}` |
| GET | `/workspace/{id}/dashboard-order` | Auth | path `id:Long`; `startDate?`, `endDate?` | Daily order report data |

### 6.3 Domain — `/domain`

| Method | Path | Auth | Params / Body | Response `data` |
|---|---|---|---|---|
| POST | `/domain/upsert` | Auth | Body `UpsertDomainPayload {id?, workspaceId:Long, domain}` | `null` |
| GET | `/domain/list` | Auth | `workspaceId:Long`, `page`, `limit` | Page of `DomainDto {id, domain}` |
| GET | `/domain/dropdown` | Auth | `workspaceId:Long` | List of `DomainDto {id, domain}` |
| POST | `/domain/delete` | Auth | Body `DeleteDomainPayload {id:Long}` | `null` |

### 6.4 Gudang (Warehouse) — `/gudang`

| Method | Path | Auth | Params / Body | Response `data` |
|---|---|---|---|---|
| POST | `/gudang` | Auth | Body `AddGudangDto {id?, namaGudang, alamat, idProvinsi, idKota, idKecamatan, idWorkspace}` | `null` |
| GET | `/gudang` | Auth | `workspaceId:Long`, `page`, `limit` | Page of `GudangDto {id, namaGudang, alamat, provinsi, kota, kecamatan}` |
| GET | `/gudang/delete` | Auth | `id:Long` | `null` |
| GET | `/gudang/{id}` | Auth | path `id:Long` | `GudangDetailResponse {id, namaGudang, alamat, provinsi:LokasiDto, kota:LokasiDto, kecamatan:LokasiDto, idWorkspace}` |

`LokasiDto = {id:Integer, nama:String}`.

### 6.5 Produk (Product) — `/produk`

| Method | Path | Auth | Params / Body | Response `data` |
|---|---|---|---|---|
| POST | `/produk` | Auth | Body `AddProdukDto` (see below) | saved product |
| GET | `/produk` | Auth | `workspaceId:Long`, `page`, `limit`, `search?` | Page of product list |
| GET | `/produk/{id}` | Auth | path `id:UUID` | product detail |
| GET | `/produk/{id}/attribut` | Auth | path `id:UUID` | product attributes list |
| GET | `/produk/{id}/pembayaran` | Auth | path `id:UUID` | product payment options |
| GET | `/produk/checkout` | **Public** | `urlCheckout:String` | checkout product detail |
| POST | `/produk/delete` | Auth | Body `List<UUID>` (product ids) | `null` |
| GET | `/produk/copy` | Auth | `idProduk:UUID` | copied product |
| GET | `/produk/list-dropdown` | Auth | `workspaceId:Long` | product dropdown list |

**`AddProdukDto`:** `id?:UUID`, `idWorkspace:Long`, `namaProduk`, `urlCheckout`, `gambarProduk:[String]`, `poinFitur:[String]`, `atributProduk:[AtributProdukDto]`, `pembayaran:[PembayaranDto]`, `idGudang:Long`, `formConfig:[ProdukFormConfigDto]`, `ekstra:[ProdukEkstraDto]`, `narasiTombol`, `testimoni:[ProdukTestimoniDto]`, `facebookPixelId`, `googleGtmId`, `embededCheckoutScript`, `embededPurchaseScript`.

Nested:
- `AtributProdukDto {id?:UUID, deskripsi, harga:Long, berat:Integer}`
- `PembayaranDto {tipe:String, config:Map}`
- `ProdukFormConfigDto {tipeField, label, placeholder, order:Integer, isMandatory:Boolean}`
- `ProdukEkstraDto {type:String, config:Map}`
- `ProdukTestimoniDto {nama, pesan, urlGambar}`

> Image/media URLs submitted as full public URLs are converted to storage paths on save and returned as public URLs on read.

### 6.6 Order — `/order`

| Method | Path | Auth | Params / Body | Response `data` |
|---|---|---|---|---|
| GET | `/order` | Auth | `workspaceId:Long`, `page`, `limit`, `idProvinsi?`, `idKota?`, `idKecamatan?`, `status?:OrderStatus`, `jenisPembayaran?:JenisPembayaran`, `statusEkspor?:Boolean`, `tanggalAwalPaid?`, `tanggalAkhirPaid?`, `tanggalAwalOrder?`, `tanggalAkhirOrder?`, `search?` | Page of order list |
| GET | `/order/export` | Auth | same filters as `/order` (no paging) | **binary `.xlsx`** (not envelope) |
| GET | `/order/abandoned` | Auth | `workspaceId:Long`, `page`, `limit`, `namaKonsumen?`, `nomorWhatsapp?` | Page of abandoned orders |
| GET | `/order/abandoned/{idAbandonedOrder}` | Auth | path `idAbandonedOrder:UUID` | abandoned order detail |
| POST | `/order/create` | **Public** | Body `CreateOrderDto {idProduk:UUID, idAtributProduk:UUID, namaLengkap, nomorWhatsapp, alamat, idProvinsi, idKota, idKecamatan, metodePembayaran, source}` | created order |
| POST | `/order/update` | Auth | Body `UpdateOrderDto {id:UUID, idProduk, idAtributProduk, namaLengkap, nomorWhatsapp, alamat, idKota, idProvinsi, idKecamatan, metodePembayaran, notes?, diskon?:Long, status:OrderStatus}` | `null` |
| POST | `/order/update-bulk` | Auth | Body `List<BulkUpdateStatus {id:UUID, status:OrderStatus}>` | `null` |
| GET | `/order/{id}` | Auth | path `id:UUID` | order detail |
| GET | `/order/{id}/logs` | Auth | path `id:UUID` | order activity logs |
| GET | `/order/status` | Auth | — | List of `OrderStatus` values |
| POST | `/order/abandoned/delete` | Auth | Body `List<DeleteAbandonedOrder {id:UUID}>` | `null` |

> **Order code format:** new orders get a 9-char code `YYMMXXXXX` (2-digit year + 2-digit month + 5-digit per-month sequence), e.g. `260600001`. Generated server-side; treat as opaque.

### 6.7 Location — `/location` (Public reads)

| Method | Path | Auth | Params | Response `data` |
|---|---|---|---|---|
| GET | `/location/province` | Public | — | List of provinces `{id, provinceName, ...}` |
| GET | `/location/city` | Public | `provinceId?:Integer` | List of cities |
| GET | `/location/district` | Public | `cityId?:Integer` | List of districts |

> **Blocked provinces:** provinces marked blocked are excluded from `/location/province`. Their cities and districts are also excluded from `/location/city` and `/location/district` (cascading). Blocking is managed via `/master/province/*` (see below).

### 6.8 Master / Admin config — `/master`

| Method | Path | Auth | Params / Body | Response `data` |
|---|---|---|---|---|
| GET | `/master/facebook-pixel` | Auth | `facebookPixelId:String` | facebook pixel data |
| GET | `/master/google-gtm` | Auth | `googleGtmId:String` | google GTM data |
| POST | `/master/upload-file` | Auth | `file` (multipart) | public media URL (string) — default media bucket |
| POST | `/master/saktiform-media` | Auth | `file` (multipart) | public media URL (string) — product bucket (moved from `/produk/uploadFile`) |
| POST | `/master/ai-key` | Auth | Body `SetAiKeyPayload {key:String}` | saved config |
| GET | `/master/ai-key` | Auth | — | AI key config (string) |
| GET | `/master/province/blocked` | Auth | — | List of `ProvinceDto {id:Integer, provinceName:String}` (blocked subset only) |
| POST | `/master/province/block` | Auth | Body `List<Integer>` (province ids) | `null` (404 if any id unknown) |
| POST | `/master/province/unblock` | Auth | Body `List<Integer>` (province ids) | `null` (404 if any id unknown) |

### 6.9 Blocked IP — `/master/blocked-ip`

| Method | Path | Auth | Params / Body | Response `data` |
|---|---|---|---|---|
| POST | `/master/blocked-ip` | Auth | Body `CreateBlockedIpDto {ipAddress:String}` | created `BlockedIp {id, ipAddress, createdAt, updatedAt}` |
| GET | `/master/blocked-ip` | Auth | `page`, `limit` | Page of blocked IPs |
| GET | `/master/blocked-ip/delete` | Auth | `id:Long` | `null` |

> Create rejects duplicates and attempts to block the server's own IP or the caller's current IP (HTTP 400). Blocked IPs cannot reach any endpoint (see §2 IP blocking).

### 6.10 Chat Template — `/template`

| Method | Path | Auth | Params / Body | Response `data` |
|---|---|---|---|---|
| GET | `/template/variable` | Auth | — | list of template variables (e.g. `{nama_customer}`) |
| GET | `/template` | Auth | `workspaceId:Long`, `page`, `limit` | Page of `ChatTemplateListDto {id, namaTemplate, kategori, mediaLink}` |
| GET | `/template/{id}` | Auth | path `id:UUID` | `DetailTemplate {id, namaTemplate, content, mediaLink}` |
| POST | `/template` | Auth | Body `AddChatTemplateDto {id?:UUID, idWorkspace:Long, namaTemplate, content, mediaLink?}` | `null` |
| GET | `/template/delete` | Auth | `id:UUID` | `null` |

> `POST /template` upserts (include `id` to update). `mediaLink` is submitted as a full public URL and returned as a public URL on reads; a null `mediaLink` on update clears it.

### 6.11 Chat / Conversation — (root paths)

> Note: this controller has no path prefix; paths are absolute as listed.

| Method | Path | Auth | Params / Body | Response `data` |
|---|---|---|---|---|
| GET | `/conversation/assigned` | Auth | `workspaceId:Long`, `page`, `limit`, `isUnread?`, `agent?`, `startDate?`, `endDate?` (`yyyy-MM-dd HH:mm`), `statusOrder?`, `keyword?`, `statusPesan?` | Page of assigned conversations |
| GET | `/conversation/unassigned` | Auth | same as assigned | Page of unassigned conversations |
| GET | `/conversation/detail` | Auth | `conversationId:UUID` | conversation detail |
| GET | `/conversation/order` | Auth | `conversationId:UUID` | order linked to conversation |
| GET | `/conversation/message` | Auth | `conversationId:UUID`, `page`, `limit`, `keyword?` | Page of messages |
| POST | `/send-message` | Auth | Body `SendMessageDto {conversationId:UUID, messageType:ChatType, mediaLink?, message?, repliedMessageId?:UUID}` | `null` |
| GET | `/conversation/takeover` | Auth | `conversationId:UUID` | `null` |
| POST | `/conversation/select-order` | Auth | Body `ConversationSelectOrder {conversationId:UUID, orderId:UUID}` | `null` |
| POST | `/conversation/quick-chat` | Auth | Body `QuickChatRequest {conversationId:UUID, templateId:UUID}` | quick chat response |
| POST | `/conversation/add-order` | Auth | Body `ChatAddOrderRequest {idProduk:UUID, idAtributProduk:UUID, namaLengkap, nomorWhatsapp, alamat, idProvinsi, idKota, idKecamatan, metodePembayaran, status:OrderStatus, notes?, diskon?:Long, source?}` | `null` |
| GET | `/agent` | Auth | `workspaceId:Long` | list of agents |
| GET | `/chat/status` | Auth | — | list of `ChatStatus` values |

### 6.12 WhatsApp — `/whatsapp`

| Method | Path | Auth | Params / Body | Response `data` |
|---|---|---|---|---|
| POST | `/whatsapp/webhook` | **Public** | Body `WebhookEnvelopeV2` (provider event payload) | `"Webhook received"` |
| POST | `/whatsapp` | Auth | Body `RegisterWhatsappDto {nomorWhatsapp:String}` | `null` |
| POST | `/whatsapp/connect` | Auth | Body `ConnectRequest {wabaId:UUID}` | connection result |
| GET | `/whatsapp` | Auth | `page`, `limit`, `search` (default `""`) | Page of WhatsApp instances |
| GET | `/whatsapp/available` | Auth | — | list of available instances |
| POST | `/whatsapp/delete` | Auth | Body `DeleteWhatsappPayload {id:UUID}` | `null` |

---

## 7. Frontend integration notes

- **Always send `Authorization: Bearer <token>`** for non-public endpoints; expect HTTP 401/403 otherwise.
- **Pass `workspaceId`** on workspace-scoped reads (orders, products, conversations, templates, gudang, domains, agents). The selected workspace comes from the `workspaces` array in the login response.
- **Check `success`** in the envelope, not just HTTP status (some handlers return 400 with `success:false` and a human message suitable for display).
- **Pagination is 1-based on input** (`page=1`) but the returned `number` is 0-based.
- **Province/city/district dropdowns** auto-exclude blocked provinces and their children — no client-side filtering needed.
- **Media fields** (product images, template `mediaLink`) are exchanged as full public URLs in both directions.
- **Order export** (`GET /order/export`) is a file download — handle as a blob, not JSON.
