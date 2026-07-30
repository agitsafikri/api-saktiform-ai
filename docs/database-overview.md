# Database Overview

PostgreSQL data model for the Saktiform AI platform. Schema is managed by Hibernate (`spring.jpa.hibernate.ddl-auto=update`); connection pooling via HikariCP (max 20, min idle 5).

The model is **multi-tenant**: most tables carry an `id_workspace` column (directly or reachable through a related entity), and `Workspace` is the tenant root.

**Conventions used throughout:**
- **31 JPA entities** (the file `ProdukPembayaranDto.java` in the entity package is a DTO, not an entity).
- Primary keys: `UUID` for high-volume transactional tables, `Long` for master/config tables, `Integer` for geographic reference tables, `String` for `OrderSequence`.
- All relationships are `FetchType.LAZY`.
- Foreign keys are mapped **read-only** (`@JoinColumn(insertable = false, updatable = false)`) alongside a writable raw id column — the raw id is what services set.
- Soft delete via `isDeleted` flag on selected tables (`produk`, `atribut_produk`, `gudang`, `account`).
- `created_at` / `updated_at` audit columns on nearly all tables.

---

## Entity ↔ Table Reference

| Entity | Table | PK type | Purpose |
|---|---|---|---|
| `Workspace` | `workspace` | Long | Top-level tenant; everything scopes to it |
| `Account` | `account` | Long | User with role; M2M with workspaces |
| `Domain` | `domain` | Long | Custom domain mapped to a workspace |
| `Produk` | `produk` | UUID | Product (catalog root aggregate) |
| `AtributProduk` | `atribut_produk` | UUID | Product variant (price, weight, description) |
| `ProdukPembayaran` | `produk_pembayaran` | Long | Payment method config per product (JSON) |
| `ProdukIklan` | `produk_iklan` | Long | Ad-platform tracking (FB Pixel / Google GTM) |
| `GambarProduk` | `gambar_produk` | Long | Product image |
| `FiturProduk` | `fitur_produk` | Long | Product feature/bullet |
| `ProdukTestimoni` | `produk_testimoni` | Long | Customer testimonial |
| `ProdukFormConfig` | `produk_form_config` | Long | Dynamic checkout form field config |
| `ProdukEkstra` | `produk_ekstra` | Long | Extra/add-on config (JSON) |
| `Order` | `"order"` (quoted) | UUID | Customer order (core transaction) |
| `AbandonedOrder` | `"abandon_order"` (quoted) | UUID | Abandoned-cart record (mirrors Order) |
| `OrderHistory` | `order_history` | UUID | Per-order audit log |
| `OrderSequence` | `order_sequence` | String | Per-date order-code sequence counter |
| `OrderContact` | `order_contact` | Long | Junction: order ↔ conversation |
| `Contact` | `contact` | Long | WhatsApp contact (phone), workspace-scoped |
| `Conversation` | `conversation` | UUID | Chat thread state |
| `ConversationLabel` | `conversation_label` | Long | Master label (name + hex color), workspace-scoped |
| `ConversationLabelLink` | `conversation_label_link` | Long | Junction: conversation ↔ label (M2M) |
| `Chat` | `chat` | UUID | Individual message in a conversation |
| `ChatTemplate` | `chat_template` | UUID | Reusable message template |
| `QuickReply` | `quick_replies` | Long | Quick-reply snippet per workspace |
| `WhatsappBusinessApi` | `whatsapp_business_api` | UUID | Registered WhatsApp instance/device |
| `Province` | `province` | Integer | Indonesian province (reference) |
| `City` | `city` | Integer | Indonesian city (reference) |
| `District` | `district` | Integer | Indonesian district (reference) |
| `Ongkir` | `ongkir` | Long | Shipping-cost lookup by district/origin |
| `Gudang` | `gudang` | Long | Warehouse |
| `AppConfig` | `app_config` | Long | Key/value runtime config |

---

## Relationships by Entity

### Tenancy & Users

**`Workspace`** (`workspace`)
- `@ManyToOne` → `WhatsappBusinessApi` via `waba_id`
- `@ManyToOne` → `Domain` via `id_domain`
- `@ManyToMany(mappedBy = "workspaces")` ← `Account`

**`Account`** (`account`)
- `@ManyToMany` → `Workspace` via join table **`account_workspace`** (`id_account` ↔ `id_workspace`)
- `role` is `@Enumerated(EnumType.STRING)` — enum **`Role { OWNER, CUSTOMER_SERVICE, ADMIN }`**

**`Domain`** (`domain`)
- `@ManyToOne` → `Workspace` via `workspace_id`

### Product Aggregate

**`Produk`** (`produk`) — catalog root
- `@ManyToOne` → `Gudang` via `id_gudang`
- `@ManyToOne` → `ProdukIklan` via `google_gtm_id` (Google GTM)
- `@ManyToOne` → `ProdukIklan` via `facebook_pixel_id` (Facebook Pixel)
- `@ManyToOne` → `Workspace` via `id_workspace`

The following all reference the parent product via `@ManyToOne` → `Produk` on `id_produk`:
- **`AtributProduk`** (`atribut_produk`) — variants
- **`ProdukPembayaran`** (`produk_pembayaran`) — payment configs
- **`GambarProduk`** (`gambar_produk`) — images
- **`FiturProduk`** (`fitur_produk`) — features
- **`ProdukTestimoni`** (`produk_testimoni`) — testimonials
- **`ProdukFormConfig`** (`produk_form_config`) — dynamic form fields
- **`ProdukEkstra`** (`produk_ekstra`) — extras/add-ons

**`ProdukIklan`** (`produk_iklan`) — standalone ad-tracking config (holds `workspaceId`, `platformIklan`, `idIklan`); referenced by `Produk`.

### Orders

**`Order`** (`"order"`)
- `@ManyToOne` → `Produk` via `id_produk`
- `@ManyToOne` → `AtributProduk` via `id_atribut_produk`
- `@ManyToOne` → `Province` via `id_provinsi`
- `@ManyToOne` → `City` via `id_kota`
- `@ManyToOne` → `District` via `id_kecamatan`
- `@ManyToOne` → `ProdukPembayaran` via `id_pembayaran`
- `@ManyToOne` → `Conversation` via `id_conversation`
- `@ManyToOne` → `Contact` via `id_contact`
- `@ManyToOne` → `Account` via `last_handle_by`

**`AbandonedOrder`** (`"abandon_order"`) — mirrors Order's structure
- `@ManyToOne` → `Produk` (`id_produk`), `AtributProduk` (`id_atribut_produk`), `Province` (`id_provinsi`), `City` (`id_kota`), `District` (`id_kecamatan`), `ProdukPembayaran` (`id_pembayaran`)

**`OrderHistory`** (`order_history`)
- `@ManyToOne` → `Order` via `order_id`

**`OrderContact`** (`order_contact`) — junction
- `@ManyToOne` → `Conversation` via `id_conversation`
- `@ManyToOne` → `Order` via `id_order`

**`OrderSequence`** (`order_sequence`) — no relationships; `String` PK (date key) + sequence value for generating order codes.

### Messaging

**`Contact`** (`contact`) — phone-based customer record; holds `idWorkspace` (no JPA relationship object), no outbound joins.

**`Conversation`** (`conversation`)
- `@OneToOne` → `Contact` via `id_contact`
- `@ManyToOne` → `Account` via `handled_by`

**`ConversationLabel`** (`conversation_label`) — master label; holds `idWorkspace` as a plain column (no mapped `@ManyToOne`), `name`, `colorHex`. Unique `(id_workspace, lower(name))` enforced by a functional index created post-startup by `LabelSchemaInitializer` (Hibernate can't declare functional indexes).

**`ConversationLabelLink`** (`conversation_label_link`) — M2M junction between `Conversation` and `ConversationLabel`. Relations are modeled as **plain columns** (`conversationId:UUID`, `labelId:Long`), not `@ManyToOne`, consistent with the tenant-isolation style. Denormalizes `idWorkspace` (Conversation has no workspace column — tenant is otherwise reached via `contact.id_workspace`). Unique `(conversation_id, label_id)` keeps assignment idempotent. Cascade on label delete is managed in the service layer (no physical FK).

**`Chat`** (`chat`)
- `@ManyToOne` → `Conversation` via `id_conversation`
- `@OneToOne` → `Chat` (self-reference) via `replied_to_id` (reply threading)

**`ChatTemplate`** (`chat_template`)
- `@ManyToOne` → `WhatsappBusinessApi` via `id_waba`
- `@ManyToOne` → (second join also on `id_waba`) — *note: two `@ManyToOne` fields both map the `id_waba` column; appears to be a modeling quirk worth reviewing.*

**`QuickReply`** (`quick_replies`)
- `@ManyToOne` → `Workspace` via `id_workspace`

**`WhatsappBusinessApi`** (`whatsapp_business_api`) — registered instance (number, apiKey, deviceId, port, status); no outbound joins.

### Geography & Logistics

**`Province`** (`province`) — reference root, no joins.

**`City`** (`city`)
- `@ManyToOne` → `Province` via `province_id`

**`District`** (`district`)
- `@ManyToOne` → `City` via `city_id`

**`Ongkir`** (`ongkir`) — shipping cost
- `@ManyToOne` → `District` via `district_id`
- `@ManyToOne` → `City` via `origin_city_id` (origin city)

**`Gudang`** (`gudang`) — warehouse
- `@ManyToOne` → `Province` via `id_provinsi`
- `@ManyToOne` → `City` via `id_kota`
- `@ManyToOne` → `District` via `id_kecamatan`
- `@ManyToOne` → `Workspace` via `id_workspace`

### Configuration

**`AppConfig`** (`app_config`) — key/value runtime config (AI keys, prompts, bot delay/quota, WhatsApp credentials); no relationships.

---

## Relationship Map (textual ERD)

```
Workspace ──┬─< Account (M2M via account_workspace)
            ├─< Domain
            ├─< Produk
            ├─< Gudang
            ├─< QuickReply
            ├─> WhatsappBusinessApi   (waba_id)
            └─> Domain                (id_domain)

Produk ──┬─< AtributProduk
         ├─< ProdukPembayaran
         ├─< GambarProduk
         ├─< FiturProduk
         ├─< ProdukTestimoni
         ├─< ProdukFormConfig
         ├─< ProdukEkstra
         ├─> Gudang
         └─> ProdukIklan (google_gtm_id, facebook_pixel_id)

Contact ──1:1── Conversation ──< Chat ──(self)── Chat (replied_to_id)
                     │
                     ├──> Account (handled_by)
                     └──< ConversationLabelLink >── ConversationLabel   (M2M; plain-column join)

Order ──> Produk, AtributProduk, ProdukPembayaran,
          Province, City, District,
          Conversation, Contact, Account (last_handle_by)
Order ──< OrderHistory
Order ──< OrderContact >── Conversation

AbandonedOrder ──> Produk, AtributProduk, ProdukPembayaran, Province, City, District

Province ──< City ──< District ──< Ongkir (and Ongkir ──> City origin)
Gudang ──> Province, City, District, Workspace

ChatTemplate ──> WhatsappBusinessApi (id_waba)
```

`>──` = many-to-one toward the named entity; `──<` = one-to-many; `1:1` = one-to-one; `M2M` = many-to-many.

---

## Notes & Observations

- **Order code generation** is handled by `OrderSequence` (per-date counter) rather than a DB sequence object, giving human-readable, date-scoped order codes per workspace.
- **`AbandonedOrder` duplicates the `Order` shape** instead of sharing a base — the two evolve independently.
- **`ChatTemplate` declares two `@ManyToOne` fields mapped to the same `id_waba` column**; this is likely an oversight (one was probably intended to join `Workspace`). Worth reviewing if template-by-workspace queries misbehave.
- **`Contact` and `ProdukIklan` store `workspaceId` as a plain column** without a mapped `@ManyToOne` to `Workspace`, unlike most tenant-scoped entities.
- **Reserved-word tables** `order` and `abandon_order` are quoted in `@Table(name = "\"...\"")` to satisfy PostgreSQL.
- **JSON columns**: `Order.configPembayaran`, `ProdukPembayaran.config`, `ProdukEkstra.config`.
- **Conversation labels** (`conversation_label`, `conversation_label_link`) model tags as a master + M2M junction with **plain-column joins** (no `@ManyToOne`) and **service-managed cascade** (no physical FK), matching the codebase's tenant-isolation conventions. The case-insensitive unique label name per workspace relies on a functional index (`lower(name)`) created at startup by `LabelSchemaInitializer` rather than a JPA constraint.