# Business Modules — Saktiform Dashboard

---

## 1. Auth

### Purpose
Handles user login, session persistence, and workspace selection. Provides the identity context (`token`, `user`, `activeWorkspace`) consumed by every other module.

### Main Pages
| Route | File |
|---|---|
| `/login` | [src/pages/login.vue](../src/pages/login.vue) |

### Components
- `InputCustom` — username and password fields
- `Modal` — "Forgot password" informational modal (no reset flow; directs user to contact admin)
- Eye / EyeOff icons — password visibility toggle

### Related APIs
| Action | Method | Endpoint |
|---|---|---|
| Login | POST | `/account/login` |

Login response shape: `{ nama, username, role, workspaces, token }`. After a successful login, `useAuthStore` stores all fields, selects `workspaces[0]` as the active workspace, and `useSidebarStore.setMenus(role)` populates the role-filtered navigation menu.

Logout is client-side only: clears Pinia state and removes `auth` and `sidebar` keys from `localStorage`. No server-side session invalidation call is made.

### Dependencies on Other Modules
- **Sidebar** — `useSidebarStore.setMenus(role)` is called immediately after login to populate navigation menus
- All modules — every module reads `useAuthStore` for `token`, `user.activeWorkspace.id`, and `user.role`

---

## 2. Dashboard (Beranda)

### Purpose
Displays workspace-level KPI summary and a time-series order chart for the active workspace. The period is user-selectable via a date-range picker.

### Main Pages
| Route | File |
|---|---|
| `/` | [src/pages/index.vue](../src/pages/index.vue) |

### Components
- `DateRangePicker` — selects the reporting period (defaults to current month)
- `BaseHighlightCard` — renders four metrics: Total Order, Total Bayar, Rasio Bayar, Unpaid Order
- `ChartComparison` — ApexCharts line chart comparing Total Order vs Total Bayar over time

### Related APIs
All called on `useWorkspaceStore`:

| Action | Method | Endpoint |
|---|---|---|
| Fetch KPI metrics | GET | `/workspace/{workspaceId}/dashboard-matrix` |
| Fetch chart data | GET | `/workspace/{workspaceId}/dashboard-order` |

Both endpoints accept `startDate` and `endDate` query parameters. A `watch` on the `dateRange` ref re-fetches both when the period changes.

### Dependencies on Other Modules
- **Auth** — reads `authStore.user.activeWorkspace.id` to scope requests to the correct workspace
- **Workspace Management** — uses `useWorkspaceStore` (defined in `pengaturan` module)

---

## 3. Chat (Inbox sub-menu — `/chat`)

> This section covers the **Inbox** sub-menu (`/chat`). The sidebar "Chat" entry is a parent group containing three sub-menus: **Inbox** (route: `chat`, path `/chat`), **Template Chat** (route: `template-chat`, path `/template-chat`), and **Campaign** (route: `campaign`, path `/campaign`).

### Purpose
Manages real-time WhatsApp conversations. Agents can view assigned and unassigned conversation queues, read message history, send text and file messages, use quick-chat templates, link orders to conversations, create new orders, edit existing orders, and take over unassigned chats.

### Main Pages
| Route | File | Description |
|---|---|---|
| `/chat` | [src/pages/chat/index.vue](../src/pages/chat/index.vue) | Split-panel: conversation list (30%) + message thread (70%) |
| `/chat/tambah-pesanan` | [src/pages/chat/tambah-pesanan/index.vue](../src/pages/chat/tambah-pesanan/index.vue) | Create a new order from a conversation |
| `/chat/edit-pesanan/:id` | [src/pages/chat/edit-pesanan/index.vue](../src/pages/chat/edit-pesanan/index.vue) | Edit an existing order linked to a conversation |
| `/chat/detail-pesanan/:id` | [src/pages/chat/[id].vue](../src/pages/chat/[id].vue) | Read-only order detail with history log |

### Components

#### Module components (`src/modules/chat/components/`)
| Component | Purpose |
|---|---|
| `ChatList` | Left panel: tabbed list of assigned/unassigned conversations with search, role filter, unread filter, and infinite scroll |
| `ChatDetail` | Right panel: message bubble thread, file attachment input, quick-chat template picker, order selector dropdown, Takeover button |
| `Dropdown` | Generic dropdown used in `ChatDetail` to render the linked-order selector |
| `form/AddOrder` | Pure presentational form used by both `tambah-pesanan` and `edit-pesanan` pages; emits all interactions upward |

#### Shared components used
`InputCustom`, `SelectCustom`, `Modal`, `ButtonCustom`, `Badge`, `TextAreaCustom`

### Real-Time Behaviour
- On mount of `/chat`, `useWebSocket().connectGlobal(workspaceId, cb)` opens a STOMP/SockJS connection and subscribes to:
  - `/topic/conversations/unassigned/{workspaceId}`
  - `/topic/conversations/assigned/{workspaceId}`
- When a chat is selected, `subscribeToChat(conversationId, cb)` subscribes to `/topic/chatroom/{conversationId}`
- On unmount of `/chat`, both subscriptions are cleaned up and the client is disconnected

WebSocket event types handled in `useChatStore.handleWebSocketEvent()`:
- `CONVERSATION_CREATED` — prepends a new chat item to the relevant list
- `CONVERSATION_UPDATED` — moves the updated chat to the top of the list, increments unread count
- `CONVERSATION_REMOVED` — removes the chat from the list

WebSocket event types handled directly in the page's `subscribeToChat` callback:
- `NEW_MESSAGE` — appends the incoming message to `chat.history`, auto-scrolls
- `CONVERSATION_DETAIL_UPDATED` / `CONVERSATION_UPDATED` — updates `handledBy`, `status`, `selectedOrder`, contact name/phone on the active chat object

### Related APIs
All called through `useChatStore`, `useOrderStore`, or `useLocationStore`:

| Action | Method | Endpoint |
|---|---|---|
| Fetch unassigned conversations | GET | `/conversation/unassigned` |
| Fetch assigned conversations | GET | `/conversation/assigned` |
| Fetch message history | GET | `/conversation/message` |
| Fetch conversation detail | GET | `/conversation/detail` |
| Fetch conversation orders | GET | `/conversation/order` |
| Send message | POST | `/send-message` |
| Upload file attachment | POST | `/master/upload-file` |
| Takeover conversation | GET | `/conversation/takeover` |
| Select linked order | POST | `/conversation/select-order` |
| Fetch chat templates (quick chat) | GET | `/template` |
| Generate template message | POST | `/conversation/quick-chat` |
| Create order | POST | `/conversation/add-order` |
| Fetch order detail | GET | `/order/{id}` |
| Fetch order logs | GET | `/order/{id}/logs` |
| Update order | POST | `/order/update` |
| Fetch agents | GET | `/agent` |
| Fetch order statuses | GET | `/order/status` |
| Fetch provinces | GET | `/location/province` |
| Fetch cities by province | GET | `/location/city` |
| Fetch districts by city | GET | `/location/district` |

### Stores Used
| Store | Source |
|---|---|
| `useChatStore` | `src/modules/chat/store/chatStore.ts` |
| `useOrderStore` | `src/modules/chat/store/orderStore.ts` |
| `useLocationStore` (chat) | `src/modules/chat/store/locationStore.ts` |

`chatStore` holds the conversation lists and pagination state. `orderStore` handles order CRUD for the chat context. `locationStore` (chat) is a duplicate of the one in `gudang` — it provides province/city/district cascading dropdowns for the order address form.

### Dependencies on Other Modules
- **Auth** — reads `activeWorkspace.id` and `username` for scoping requests and determining `canTakeover`
- **Produk** — the create/edit order pages call `useProdukStore.onIndex`, `onGetProductAttribute`, and `onGetProductPayment` to populate the product, variant, and payment method selects
- **Chat Templates** (Pengaturan) — the quick-chat picker fetches templates from `/template`, which are managed under the Workspace Management module in Pengaturan

---

## 4. Pesanan (Orders)

### Purpose
Provides a full view of all orders and abandoned carts across the active workspace. Agents and admins can view, filter, edit, bulk-update status, and export orders.

### Main Pages
| Route | File | Description |
|---|---|---|
| `/pesanan` | [src/pages/pesanan/index.vue](../src/pages/pesanan/index.vue) | Paginated order list with status filter and export |
| `/pesanan/abandoned-cart` | [src/pages/pesanan/abandoned-cart.vue](../src/pages/pesanan/abandoned-cart.vue) | List of abandoned (incomplete) carts with edit/delete |

### Components

#### Module components (`src/modules/pesanan/components/`)
| Component | Purpose |
|---|---|
| `listPesanan` | Order data table with search, status filter, date range filter, and bulk status update |
| `ModalDetailPesanan` | Modal showing full order details inline |
| `ModalEditPesanan` | Modal form for editing an existing order |
| `ListAbandonedCart` | Paginated table of abandoned carts |
| `ModalEditAbandonedCart` | Modal form for editing an abandoned cart entry |

#### Shared components used
`TableCustom`, `PaginationCustom`, `Modal`, `SelectCustom`, `DateRangePicker`, `ButtonCustom`, `Badge`

### Related APIs
All called through `usePesananStore`:

| Action | Method | Endpoint |
|---|---|---|
| Fetch orders (paginated) | GET | `/order` |
| Fetch order detail | GET | `/order/{id}` |
| Fetch order logs | GET | `/order/{id}/logs` |
| Update single order | POST | `/order/update` |
| Bulk update order status | POST | `/order/update-bulk` |
| Fetch available statuses | GET | `/order/status` |
| Fetch abandoned carts | GET | `/order/abandoned` |
| Fetch abandoned cart detail | GET | `/order/abandoned/{id}` |
| Delete abandoned cart(s) | POST | `/order/abandoned/delete` |
| Export orders | GET | `/order/export` (returns blob) |

### Dependencies on Other Modules
- **Auth** — reads `activeWorkspace.id` to scope all order queries

---

## 5. Produk (Products)

### Purpose
Manages the product catalogue for the active workspace. Products have multiple variants (attributes) each with their own price and weight, multiple accepted payment methods, feature bullet points, images, testimonials, checkout configuration, and optional tracking integrations (Facebook Pixel, Google GTM).

### Main Pages
| Route | File | Description |
|---|---|---|
| `/produk` | [src/pages/produk/index.vue](../src/pages/produk/index.vue) | Product list with search and delete |
| `/produk/tambah-produk` | [src/pages/produk/tambah-produk.vue](../src/pages/produk/tambah-produk.vue) | Create a new product |
| `/produk/:id` | [src/pages/produk/[id].vue](../src/pages/produk/[id].vue) | Edit an existing product |

### Components

#### Module components (`src/modules/produk/components/`)
| Component | Purpose |
|---|---|
| `ListProduk` | Product table with search, pagination, multi-select delete, and copy |
| `FormProduk` | Multi-section product form: basic info, images (multi-upload), feature points, product variants (atributProduk array), payment methods, warehouse, checkout form config, extra fields, CTA text, testimonials, pixel/GTM IDs, embedded scripts |

#### Shared components used
`TableCustom`, `PaginationCustom`, `InputCustom`, `SelectCustom`, `MultipleFileUpload`, `TextAreaCustom`, `ButtonCustom`, `SwitchButton`, `Modal`

### Product Data Model (from `initProduk`)
A product record contains:
- `namaProduk`, `urlCheckout`, `narasiTombol` — basic info and CTA text
- `gambarProduk` / `fileGambarProduk` — image URLs + upload file objects
- `poinFitur` — array of feature bullet strings
- `atributProduk` — array of `{ deskripsi, harga, berat }` variant objects
- `pembayaran` — accepted payment methods
- `idGudang` — linked warehouse
- `formConfig` — custom checkout form fields
- `ekstra` — extra configuration fields
- `testimoni` — array of `{ nama, pesan, urlGambar, file }` testimonial objects
- `facebookPixelId`, `googleGtmId`, `embededCheckoutScript`, `embededPurchaseScript` — tracking/embedding integrations

### Related APIs
All called through `useProdukStore`:

| Action | Method | Endpoint |
|---|---|---|
| Fetch product list | GET | `/produk` |
| Fetch product for dropdowns | GET | `/produk/list-dropdown` |
| Fetch product detail | GET | `/produk/{id}` |
| Create product | POST | `/produk` |
| Delete products | POST | `/produk/delete` |
| Copy product | GET | `/produk/copy` |
| Upload product image | POST | `/produk/uploadFile` |
| Fetch product variants | GET | `/produk/{id}/attribut` |
| Fetch product payment methods | GET | `/produk/{id}/pembayaran` |

### Dependencies on Other Modules
- **Auth** — reads `activeWorkspace.id` when fetching the product list
- **Chat** — the Create/Edit Order pages in the Chat module call `useProdukStore.onIndex`, `onGetProductAttribute`, and `onGetProductPayment` to populate order form dropdowns

---

## 6. Pengaturan (Settings)

The Settings section is only accessible to the `OWNER` role. It is divided into five sub-sections, each navigated to from the `ListPengaturan` index component.

---

### 6.1 User Management

#### Purpose
Create, view, and delete user accounts. Supports assigning roles (`OWNER`, `ADMIN`, `CUSTOMER_SERVICE`) and resetting passwords.

#### Main Pages
| Route | File |
|---|---|
| `/pengaturan/user` | [src/pages/pengaturan/user/index.vue](../src/pages/pengaturan/user/index.vue) |

#### Components
- `ListUserManagement` — paginated user table with create form, delete, and bulk delete

#### Related APIs
All called through `useUserStore`:

| Action | Method | Endpoint |
|---|---|---|
| Fetch users | GET | `/account` |
| Fetch user detail | GET | `/account/{id}` |
| Create user | POST | `/account` |
| Delete user | POST | `/account/delete` |
| Bulk delete users | POST | `/account/delete-bulk` |
| Reset user password | POST | `/account/reset-password` |
| Fetch available roles | GET | `/account/role` |
| Fetch users for dropdowns | GET | `/account/list` |

#### Dependencies on Other Modules
- **Auth** — none beyond the route guard (OWNER-only)

---

### 6.2 Workspace Management

#### Purpose
Create and configure workspaces. Each workspace has sub-configuration for: assigned users, warehouses (gudang), a checkout domain, and chat message templates.

#### Main Pages
| Route | File | Description |
|---|---|---|
| `/pengaturan/workspace` | [src/pages/pengaturan/workspace/index.vue](../src/pages/pengaturan/workspace/index.vue) | Workspace list |
| `/pengaturan/workspace/:id` | [src/pages/pengaturan/workspace/[id].vue](../src/pages/pengaturan/workspace/[id].vue) | Workspace detail with all 4 sub-tabs |

#### Components
| Component | Location | Purpose |
|---|---|---|
| `ListWorkspaceManagement` | `workspace-management/components/` | Paginated workspace list with create/edit |
| `DetailWorkspace` | `workspace-management/components/` | Workspace detail container with 4 sub-sections |
| `ListWorkspaceUser` | `workspace-management/user/components/` | Lists users assigned to the workspace |
| `ModalAddWorkspaceUser` | `workspace-management/user/components/` | Modal to add an existing account to the workspace |
| `ListWorkspaceGudang` | `workspace-management/gudang/components/` | Lists warehouses with create/edit/delete |
| `ModalFormGudang` | `workspace-management/gudang/components/` | Create/edit warehouse form with province/city/district cascade |
| `ListWorkspaceDomain` | `workspace-management/domain/components/` | Lists checkout domains with upsert/delete |
| `ModalFormDomain` | `workspace-management/domain/components/` | Create/edit domain form |
| `ListWorkspaceTemplateChat` | `workspace-management/template-chat/components/` | Lists chat templates with create/edit/delete |
| `ModalFormTemplateChat` | `workspace-management/template-chat/components/` | Create/edit template with variable picker, optional media attachment upload, and a `workspaceId` prop (falls back to `route.params.id`). A `FileUpload` input allows attaching an optional media file; the file is uploaded to `POST /master/saktiform-media`, which returns a URL stored as `mediaLink` in the template payload. |

#### Related APIs
Called through `useWorkspaceStore`, `useGudangStore`, `useDomainStore`, `useTemplateChatStore`, `useLocationStore` (gudang), and `useUserStore`:

**Workspace**
| Action | Method | Endpoint |
|---|---|---|
| Fetch workspaces | GET | `/workspace` |
| Fetch workspace for dropdowns | GET | `/workspace/list` |
| Fetch workspace detail | GET | `/workspace/{id}` |
| Create workspace | POST | `/workspace` |
| Update workspace | POST | `/workspace/update` |
| Fetch workspace users | GET | `/workspace/{id}/account` |
| Add user to workspace | POST | `/workspace/{id}/account` |
| Remove user from workspace | GET | `/workspace/{id}/account/remove` |
| Get workspace domain | GET | `/workspace/{id}/domain` |
| Set workspace domain | POST | `/workspace/domain` |

**Warehouse (Gudang)**
| Action | Method | Endpoint |
|---|---|---|
| Fetch warehouses | GET | `/gudang` |
| Fetch warehouse detail | GET | `/gudang/{id}` |
| Create warehouse | POST | `/gudang` |
| Delete warehouse | GET | `/gudang/delete` |

**Domain**
| Action | Method | Endpoint |
|---|---|---|
| Fetch domains | GET | `/domain/list` |
| Upsert domain | POST | `/domain/upsert` |
| Delete domain | POST | `/domain/delete` |
| Fetch domains for dropdowns | GET | `/domain/dropdown` |

**Chat Templates**
| Action | Method | Endpoint |
|---|---|---|
| Fetch templates | GET | `/template` |
| Fetch template detail | GET | `/template/{id}` |
| Create/update template | POST | `/template` |
| Delete template | GET | `/template/delete` |
| Fetch template variables | GET | `/template/variable` |

**Location (used by warehouse form)**
| Action | Method | Endpoint |
|---|---|---|
| Fetch provinces | GET | `/location/province` |
| Fetch cities | GET | `/location/city` |
| Fetch districts | GET | `/location/district` |

#### Dependencies on Other Modules
- **User Management** — `useUserStore.getAvailableUsers()` is called to populate the user picker when adding accounts to a workspace
- **Chat** — chat templates created here are consumed by the quick-chat template picker in `ChatDetail`

---

### 6.3 WhatsApp Business

#### Purpose
Register and manage WhatsApp Business API (WABA) accounts. Allows connecting a WABA ID to the platform.

#### Main Pages
| Route | File |
|---|---|
| `/pengaturan/whatsapp-business` | [src/pages/pengaturan/whatsapp-business/index.vue](../src/pages/pengaturan/whatsapp-business/index.vue) |

#### Components
- `ListWhatsappBusiness` — lists registered WhatsApp Business numbers with add/delete and a connect action

#### Related APIs
All called through `useWhatsappStore`:

| Action | Method | Endpoint |
|---|---|---|
| Fetch WhatsApp accounts | GET | `/whatsapp` |
| Fetch account detail | GET | `/whatsapp/{id}` |
| Register new account | POST | `/whatsapp` |
| Delete account | POST | `/whatsapp/delete` |
| Connect WABA to platform | POST | `/whatsapp/connect` |
| Fetch available numbers (dropdown) | GET | `/whatsapp/available` |

#### Dependencies on Other Modules
- None directly from the UI. WhatsApp accounts are linked to workspaces by the backend when `wabaId` is supplied during workspace creation.

---

### 6.4 Model AI

#### Purpose
Store and retrieve the API key used by the platform's AI features (quick-chat generation).

#### Main Pages
| Route | File |
|---|---|
| `/pengaturan/model-ai` | [src/pages/pengaturan/model-ai/index.vue](../src/pages/pengaturan/model-ai/index.vue) |

#### Components
The page is self-contained: `InputCustom` with a password/text toggle and a save button. No separate module component.

#### Related APIs
Called through `useMasterStore`:

| Action | Method | Endpoint |
|---|---|---|
| Fetch current AI key | GET | `/master/ai-key` |
| Save AI key | POST | `/master/ai-key` |

`useMasterStore` also contains actions for Facebook Pixel and Google GTM (`/master/facebook-pixel`, `/master/google-gtm`), but these are not wired to any page in the current implementation — the actions exist in the store but no page calls them.

#### Dependencies on Other Modules
- **Chat** — the AI key stored here is used server-side to power the `generateQuickChatMessage` call in `chatStore`, but there is no direct frontend dependency beyond sharing the same API base URL.

---

### 6.5 Disable Provinsi

#### Purpose
Block specific provinces so they are excluded from the active workspace's checkout flow. Owners can view currently blocked provinces, add new ones via bulk multi-select, and unblock them one at a time.

#### Main Pages
| Route | File |
|---|---|
| `/pengaturan/disable-provinsi` | `src/pages/pengaturan/disable-provinsi/index.vue` |

#### Components
- `ListProvinceException` — table of blocked provinces with bulk-add modal (MultipleSelectCustom) and per-row unblock confirm modal

#### Related APIs
All called through `useProvinceExceptionStore`:

| Action | Method | Endpoint |
|---|---|---|
| Fetch blocked provinces | GET | `/master/province/blocked` |
| Fetch available provinces | GET | `/location/province` (reused) |
| Block provinces (bulk) | POST | `/master/province/block` |
| Unblock a province | POST | `/master/province/unblock` |

#### Dependencies on Other Modules
- **Auth** — none beyond the route guard (OWNER-only)
- **Location** — reuses `/location/province` to populate the available provinces multi-select

---

## 7. Campaign

### Purpose
Placeholder module for campaign management. The sidebar sub-menu entry "Campaign" (under the Chat group) routes here. Feature content is pending implementation.

### Main Pages
| Route | File |
|---|---|
| `/campaign` | `src/pages/campaign/index.vue` |

### Components
None yet — page renders a placeholder.

### Related APIs
None yet.

### Dependencies on Other Modules
- **Auth** — reads `activeWorkspace.id` (will be used when implemented)
