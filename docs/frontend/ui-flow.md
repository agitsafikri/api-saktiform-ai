# UI Flow — Saktiform Dashboard

All navigation is handled by Vue Router 4 with file-based routing via `vite-plugin-pages`. Navigation guards defined in [src/auth.ts](../src/auth.ts) enforce authentication and role rules on every route change. The sidebar is the primary navigation surface for authenticated users.

---

## Route Index

| Route Name | Path | Layout | requiredAuth | rolePermission |
|---|---|---|---|---|
| `login` | `/login` | default | 2 (logged-out only) | — |
| `beranda` | `/` | dashboardLayout | 1 | OWNER, ADMIN, CS |
| `not-found` | `/not-found` | dashboardLayout | 1 | — |
| `chat` | `/chat` | dashboardLayout | 1 | OWNER, ADMIN, CS |
| `detail-pesanan` | `/chat/detail-pesanan/:id` | dashboardLayout | 1 | OWNER, ADMIN, CS |
| `tambah-pesanan` | `/chat/tambah-pesanan` | dashboardLayout | 1 | OWNER, ADMIN, CS |
| `edit-pesanan` | `/chat/edit-pesanan/:id` | dashboardLayout | 1 | OWNER, ADMIN, CS |
| `template-chat` | `/template-chat` | dashboardLayout | 1 | OWNER, ADMIN, CS |
| `campaign` | `/campaign` | dashboardLayout | 1 | OWNER, ADMIN, CS |
| `pesanan` | `/pesanan` | dashboardLayout | 1 | OWNER, ADMIN, CS |
| `abandoned-cart` | `/pesanan/abandoned-cart` | dashboardLayout | 1 | OWNER, ADMIN, CS |
| `produk` | `/produk` | dashboardLayout | 1 | OWNER, ADMIN, CS |
| `tambah-produk` | `/produk/tambah-produk` | dashboardLayout | 1 | OWNER, ADMIN, CS |
| `ubah-produk` | `/produk/:id` | dashboardLayout | 1 | OWNER, ADMIN, CS |
| `pengaturan` | `/pengaturan` | dashboardLayout | 1 | **OWNER only** |
| `pengaturan-user` | `/pengaturan/user` | dashboardLayout | 1 | **OWNER only** |
| `pengaturan-whatsapp` | `/pengaturan/whatsapp-business` | dashboardLayout | 1 | **OWNER only** |
| `pengaturan-model-ai` | `/pengaturan/model-ai` | dashboardLayout | 1 | **OWNER only** |
| `pengaturan-workspace` | `/pengaturan/workspace` | dashboardLayout | 1 | **OWNER only** |
| `detail-workspace` | `/pengaturan/workspace/:id` | dashboardLayout | 1 | **OWNER only** |
| `pengaturan-disable-provinsi` | `/pengaturan/disable-provinsi` | dashboardLayout | 1 | **OWNER only** |

---

## Global Navigation Guards

Guards are evaluated on every route change in [src/auth.ts](../src/auth.ts):

1. **`requiredAuth === 1` and no token** → redirect to `login`
2. **`requiredAuth === 2` and token present** → redirect to `beranda`
3. **`rolePermission` defined and user's role not in array** → redirect to `not-found`
4. **Always** → sets `headerContentStore.breadcrumb` from `route.meta.breadcrumb`

---

## Sidebar Navigation

| Role | Available menus |
|---|---|
| `OWNER` | Dashboard, Produk, Pesanan, Chat (group), Pengaturan |
| `ADMIN` | Dashboard, Produk, Pesanan, Chat (group) |
| `CUSTOMER_SERVICE` | Dashboard, Produk, Pesanan, Chat (group) |

The **Chat** menu is a collapsible parent group with three sub-menu items:
- **Inbox** → `/chat` (route: `chat`)
- **Template Chat** → `/template-chat` (route: `template-chat`)
- **Campaign** → `/campaign` (route: `campaign`)

When the Chat group is **collapsed** and a child route is active, the Chat parent item is highlighted. When expanded, only the active child is highlighted.

The sidebar menu list is **not persisted** to localStorage — it is rebuilt from source code by `useSidebarStore.setMenus(role)` on every session start (called at login and in `beforeEach` when `menus.length === 0`).

The sidebar also contains a workspace switcher. Selecting a different workspace calls `authStore.setActiveWorkspace(workspace)`, resets the sidebar menu, and redirects to `beranda`.

---

## 1. Login Flow

**Entry:** any unprotected URL, or redirect from `requiredAuth: 1` guard

```
Browser loads app
  └── requiredAuth guard fires
        ├── no token → redirect to /login
        └── has token → continue to requested page (or beranda)

/login (requiredAuth: 2)
  ├── user already logged in → redirected to beranda by guard
  └── user not logged in
        ├── username + password form
        ├── "Lupa Password?" link → opens forget-password modal (no route change)
        └── submit button
              ├── calls authStore.login({ username, password })
              ├── on success
              │     ├── authStore stores token, user profile, workspaces
              │     ├── useSidebarStore.setMenus(role) builds role-filtered menu
              │     └── router.push({ name: 'beranda' })
              └── on error → alertStore shows error toast (stays on /login)
```

**Guards:**
- `requiredAuth: 2` prevents logged-in users from accessing this page

---

## 2. Dashboard Flow

**Entry:** `beranda` — the app's default landing page after login

```
/  (beranda)
  ├── onMounted
  │     ├── workspaceStore.onGetDashboardMatrix(workspaceId, dateRange)
  │     └── workspaceStore.onGetDashboardOrder(workspaceId, dateRange)
  ├── watch: dateRange changes → re-fetches both
  ├── watch: authStore.activeWorkspace changes → re-fetches both
  └── renders
        ├── 4 KPI cards (total order, total paid, payment ratio, unpaid)
        │     └── date range picker to filter all cards
        └── ChartComparison (order count vs paid count per day)
```

**Navigation out:**
- Sidebar menu links lead to all other main sections
- No programmatic `router.push` from this page

---

## 3. Product Flow

```
/produk  (produk)
  ├── ListProduk component mounts
  │     ├── onIndex() fetches product list (page, limit, workspaceId, search)
  │     ├── onGetAvailableDomain() fetches domain list for workspace domain picker
  │     └── renders paginated Table with Sort / Search
  │
  ├── "Tambah Produk" button → router.push({ name: 'tambah-produk' })
  │
  ├── Row action: Edit → router.push({ name: 'ubah-produk', params: { id } })
  │
  ├── Row action: Copy → produkStore.onCopyData(id) → refetches list
  │
  ├── Row action: Delete → ConfirmModal → produkStore.onDelete([id]) → refetches list
  │
  └── Bulk delete → ConfirmModal → produkStore.onDelete(selectedIds) → refetches list

/produk/tambah-produk  (tambah-produk)
  └── FormProduk component (create mode)
        ├── form fields: name, images, features, variants (atribut), payments,
        │   warehouse, form config, testimonials, tracking IDs, checkout URL
        ├── produkStore.onUploadProductFile() for each image upload
        ├── submit → produkStore.onStore(payload)
        ├── on success → router.push({ name: 'produk' })
        └── "Batal" / back → router.back()

/produk/:id  (ubah-produk)
  └── FormProduk component (edit mode)
        ├── onMounted → produkStore.onDetail(id) pre-fills all fields
        ├── same form fields and upload flow as create
        ├── submit → produkStore.onStore(payload) (same endpoint, id included)
        ├── on success → router.push({ name: 'produk' })
        └── "Batal" / back → router.back()
```

---

## 4. Checkout Flow

> The "checkout" in this system refers to the order creation process initiated from the chat interface. There is no standalone customer-facing checkout page in this dashboard; agents create orders on behalf of customers.

```
/chat  (chat) — agent selects a conversation
  └── "Tambah Pesanan" button → router.push({ name: 'tambah-pesanan' })

/chat/tambah-pesanan  (tambah-pesanan)
  ├── onMounted
  │     ├── produkStore.onIndex() loads product dropdown
  │     └── locationStore.fetchProvinces() loads province dropdown
  │
  ├── user selects product
  │     ├── produkStore.onGetProductAttribute(productId) loads variants
  │     └── produkStore.onGetProductPayment(productId) loads payment methods
  │
  ├── user selects province → locationStore.fetchCities(provinceId)
  ├── user selects city → locationStore.fetchDistricts(cityId)
  │
  ├── form fields: product, variant, payment method, customer name, phone,
  │   address, province, city, district, status, notes, discount
  │
  ├── "Simpan" button → shows ConfirmModal
  │     └── confirm → orderStore.createOrder(payload, { source: 'chat' })
  │           ├── on success → router.push({ name: 'chat' })
  │           └── on error → alertStore shows error toast
  │
  └── "Batal" / back → router.back()
```

---

## 5. Order Flow

```
/pesanan  (pesanan)
  └── ListPesanan component mounts
        ├── onIndex() fetches orders with filters
        │     params: page, limit, workspaceId, search, searchBy, status,
        │             jenisPembayaran, statusEkspor, date ranges, location filters
        ├── onGetStatus() loads status filter options
        ├── fetchProvinces() for location filter
        │
        ├── Row action: View → ModalDetailPesanan
        │     └── pesananStore.onDetail(id) + pesananStore.onGetLogs(id)
        │
        ├── Row action: Edit → ModalEditPesanan
        │     ├── pesananStore.onDetail(id) pre-fills form
        │     ├── produkStore.onList() for product dropdown
        │     ├── produkStore.onGetProductAttribute(productId) for variants
        │     ├── produkStore.onGetProductPayment(productId) for payments
        │     ├── location cascade: province → city → district
        │     └── submit → pesananStore.onUpdate(payload)
        │
        ├── Row action: Bulk update status → pesananStore.onUpdateBulk([{id, status}])
        │
        ├── "Export" button → pesananStore.onExport(filters) → downloads Pesanan.xlsx
        │
        └── "Abandoned Cart" tab/link → router.push({ name: 'abandoned-cart' })

/pesanan/abandoned-cart  (abandoned-cart)
  └── ListAbandonedCart component mounts
        ├── onIndexAbandoned() fetches abandoned cart list
        │     params: page, limit, workspaceId, search
        │
        ├── Row action: View → ModalDetailAbandonedCart
        │     └── pesananStore.onDetailAbandoned(id)
        │
        ├── Row action: Delete → ConfirmModal → pesananStore.onDeleteAbandoned([{id}])
        │
        └── "Kembali" link → router.push({ name: 'pesanan' })
```

### Order Detail from Chat

```
/chat  (chat) — within open conversation
  └── order list panel → click order row
        └── router.push({ name: 'detail-pesanan', params: { id } })

/chat/detail-pesanan/:id  (detail-pesanan)
  ├── onMounted → Promise.all([
  │     orderStore.fetchOrderDetail(id),
  │     orderStore.fetchOrderLogs(id)
  │   ])
  ├── renders: recipient info, product+variant, pricing breakdown, log history
  └── "Tutup" / back arrow → router.back()
```

### Order Edit from Chat

```
/chat  (chat) — within open conversation
  └── order list panel → "Edit" action
        └── router.push({ name: 'edit-pesanan', params: { id } })

/chat/edit-pesanan/:id  (edit-pesanan)
  ├── onMounted
  │     ├── produkStore.onIndex(), locationStore.fetchProvinces()
  │     └── orderStore.fetchOrderDetail(id) → pre-fills all form fields
  │           └── cascades: loads cities, districts, variants, payments
  │                 from the loaded order's existing values
  │
  ├── form fields: same as tambah-pesanan
  │   note: nomorHandphone field is read-only
  │
  ├── "Simpan" → ConfirmModal → orderStore.updateOrder(payload)
  │     ├── on success → router.back()
  │     └── on error → alertStore shows error toast
  │
  └── "Batal" → router.back()
```

---

## 6. Chat Flow

```
/chat  (chat)
  ├── onMounted
  │     ├── chatStore.fetchUnassignedChats() — tab 1
  │     ├── chatStore.fetchAssignedChats() — tab 2
  │     └── useWebSocket().connectGlobal(workspaceId, handleWebSocketEvent)
  │           └── subscribes to workspace-level STOMP topics:
  │                 /topic/conversation/workspace/{workspaceId}/unassigned
  │                 /topic/conversation/workspace/{workspaceId}/assigned
  │
  ├── onUnmounted
  │     └── disconnect() — closes STOMP connection
  │
  ├── Chat list panel (left)
  │     ├── Tab "Belum Ditangani" (unassigned) / "Ditangani" (assigned)
  │     ├── Search bar → chatStore.fetchChats(keyword)
  │     ├── Filter panel: agent, status order, message status, date range, unread
  │     ├── Infinite scroll → chatStore.fetchChats(nextPage, append: true)
  │     └── Click on chat row → handleSelectChat(chat)
  │           ├── Promise.all([
  │           │     chatStore.fetchChatMessages(chatId, page=1),
  │           │     chatStore.fetchConversationOrders(chatId),
  │           │     chatStore.fetchConversationDetail(chatId)
  │           │   ])
  │           └── subscribeToChat(conversationId, handleIncomingMessage)
  │
  ├── Chat detail panel (right) — visible when a chat is selected
  │     ├── Message thread (ChatDetail component)
  │     │     ├── Scroll up → handleLoadMoreMessages() → fetches previous page
  │     │     ├── Search → handleSearchMessage(keyword) → refetches with keyword
  │     │     ├── Reply — sets repliedMessageId before send
  │     │     ├── File attachment
  │     │     │     └── chatStore.uploadChatFile(file) → gets mediaLink
  │     │     ├── Send message → chatStore.sendMessage({ conversationId,
  │     │     │     messageType, message, mediaLink, repliedMessageId })
  │     │     │     └── new message arrives via WebSocket, not response
  │     │     ├── Quick-chat button → template picker modal
  │     │     │     ├── chatStore.fetchTemplates(workspaceId)
  │     │     │     └── select template → chatStore.generateQuickChatMessage()
  │     │     │           └── fills message input with generated text
  │     │     └── Takeover button (if chat unassigned or handled by other)
  │     │           └── chatStore.takeoverChat(conversationId)
  │     │
  │     ├── Order list panel
  │     │     ├── Shows orders linked to the conversation
  │     │     ├── "Select" radio → chatStore.selectOrder(conversationId, orderId)
  │     │     ├── "Detail" → router.push({ name: 'detail-pesanan', params: { id } })
  │     │     ├── "Edit" → router.push({ name: 'edit-pesanan', params: { id } })
  │     │     └── "Tambah Pesanan" → router.push({ name: 'tambah-pesanan' })
  │     │
  │     └── Back button (mobile) → handleBack() → clears selection, unsubscribes chat
  │
  └── WebSocket event dispatch (handleWebSocketEvent)
        ├── CONVERSATION_CREATED → prepend to relevant chat list
        ├── CONVERSATION_UPDATED → update item in chat list
        └── CONVERSATION_REMOVED → remove item from chat list
```

**Real-time message flow:**

```
incoming STOMP frame on /topic/chatroom/{conversationId}
  └── handleIncomingMessage(frame)
        ├── NEW_MESSAGE → mapMessageToUI(raw) → append to message list
        ├── CONVERSATION_DETAIL_UPDATED → update conversationDetail
        └── CONVERSATION_UPDATED → update chat item in list
```

---

## 7. Settings Flow

> All settings routes are restricted to the `OWNER` role. `ADMIN` and `CUSTOMER_SERVICE` users do not see the Pengaturan menu and are redirected to `not-found` if they attempt direct URL access.

```
/pengaturan  (pengaturan) — OWNER only
  └── ListPengaturan component
        ├── Card: User Management → router.push({ name: 'pengaturan-user' })
        ├── Card: Workspace Management → router.push({ name: 'pengaturan-workspace' })
        ├── Card: Model AI → router.push({ name: 'pengaturan-model-ai' })
        ├── Card: WhatsApp Business → router.push({ name: 'pengaturan-whatsapp' })
        └── Card: Disable Provinsi → router.push({ name: 'pengaturan-disable-provinsi' })
```

### User Management

```
/pengaturan/user  (pengaturan-user)
  └── ListUserManagement component
        ├── onMounted: userStore.onIndex() + userStore.getRoles()
        ├── Table with search and pagination
        │
        ├── "Tambah User" button → opens create modal
        │     ├── form: nama, username, password, confirmPassword, role, workspaces
        │     │     (workspaces: MultipleSelectCustom from userStore.getAvailableUsers)
        │     └── submit → userStore.onStore(payload) → refetch list
        │
        ├── Row action: Edit → opens edit modal
        │     ├── userStore.onDetail(id) pre-fills form
        │     └── submit → userStore.onStore(payload with id) → refetch list
        │
        ├── Row action: Reset Password → opens reset modal
        │     └── submit → userStore.onResetPassword({id, password, confirmPassword})
        │
        └── Row action: Delete → ConfirmModal → userStore.onDelete(id) → refetch list
```

### Workspace Management

```
/pengaturan/workspace  (pengaturan-workspace)
  └── ListWorkspaceManagement component
        ├── onMounted: workspaceStore.onIndex() + workspaceStore.onList()
        ├── Table with search and pagination
        │
        ├── "Tambah Workspace" button → opens create modal
        │     └── submit → workspaceStore.onStore(payload) → refetch list
        │
        └── Row action: Detail → router.push({ name: 'detail-workspace', params: { id } })

/pengaturan/workspace/:id  (detail-workspace)
  └── DetailWorkspace component
        ├── onMounted
        │     ├── workspaceStore.onDetail(id) loads workspace data
        │     ├── whatsappStore.onGetAvailableWhatsapp() for WABA selector
        │     └── workspaceStore.onGetWorkspaceDomain(id)
        │
        ├── Workspace info form (name, WABA selector)
        │     └── submit → workspaceStore.onUpdate(payload)
        │
        ├── Users sub-section
        │     ├── workspaceStore.onGetWorkspaceAccount(workspaceId)
        │     ├── "Add User" → ModalAddWorkspaceUser
        │     │     ├── userStore.getAvailableUsers() for dropdown
        │     │     └── submit → workspaceStore.onStoreWorkspaceAccount(idAccount)
        │     └── Row action: Remove → workspaceStore.onDeleteWorkspaceAccount(idAccount)
        │
        ├── Gudang (Warehouse) sub-section
        │     ├── gudangStore.onIndex(workspaceId)
        │     ├── "Tambah Gudang" → ModalFormGudang
        │     │     ├── location cascade: provinces → cities → districts
        │     │     └── submit → gudangStore.onStore(payload)
        │     ├── Row action: Edit → pre-fills modal via gudangStore.onDetail(id)
        │     └── Row action: Delete → ConfirmModal → gudangStore.onDelete(id)
        │
        ├── Domain sub-section
        │     ├── domainStore.onIndex(workspaceId)
        │     ├── "Tambah Domain" → ModalFormDomain
        │     │     └── submit → domainStore.onStore(payload) (POST /domain/upsert)
        │     └── Row action: Delete → ConfirmModal → domainStore.onDelete(id)
        │
        ├── Template Chat sub-section
        │     ├── templateChatStore.onIndex(workspaceId)
        │     ├── "Tambah Template" → ModalFormTemplateChat
        │     │     ├── templateChatStore.onGetTemplateVariable() for variable picker
        │     │     └── submit → templateChatStore.onStore(payload)
        │     ├── Row action: Edit → pre-fills via templateChatStore.onDetail(id)
        │     └── Row action: Delete → ConfirmModal → templateChatStore.onDelete(id)
        │
        └── "Kembali" → router.push({ name: 'pengaturan-workspace' })
```

### WhatsApp Business

```
/pengaturan/whatsapp-business  (pengaturan-whatsapp)
  └── ListWhatsappBusiness component
        ├── onMounted: whatsappStore.onIndex()
        ├── Table with search and pagination
        │
        ├── "Tambah" button → opens create modal
        │     ├── form: nomorWhatsapp
        │     └── submit → whatsappStore.onStore(payload) → refetch list
        │
        ├── Row action: Connect → whatsappStore.onConnect({ wabaId })
        ├── Row action: Edit → opens edit modal → whatsappStore.onStore(payload)
        └── Row action: Delete → ConfirmModal → whatsappStore.onDelete(id)
```

### Model AI

```
/pengaturan/model-ai  (pengaturan-model-ai)
  ├── onMounted: masterStore.onGetAI() → populates API key field
  ├── single field: API key (toggle text/password visibility)
  └── "Simpan" button → masterStore.onStoreAI({ key })
```

### Disable Provinsi

```
/pengaturan/disable-provinsi  (pengaturan-disable-provinsi) — OWNER only
  └── ListProvinceException component
        ├── onMounted: provinceExceptionStore.onGetBlocked()
        ├── Table: kolom provinceName, aksi (unblock)
        │
        ├── "Tambah Provinsi" button → opens bulk-add modal
        │     ├── provinceExceptionStore.onGetAvailable() loads province list
        │     ├── MultipleSelectCustom: select one or more provinces to block
        │     └── submit → provinceExceptionStore.onBlock([...ids]) → refetch
        │
        └── Row action: Unblock → ConfirmModal
              └── confirm → provinceExceptionStore.onUnblock(id) → refetch
```

---

## 8. Template Chat (Sidebar)

Template Chat is accessible from the sidebar as a sub-menu of the Chat group, and also remains accessible from `/pengaturan/workspace/:id` as a sub-tab of the workspace detail.

When accessed from the sidebar at `/template-chat`, the page reads the active workspace from `authStore.user.activeWorkspace.id` and passes it as `workspaceId` prop to `ListWorkspaceTemplateChat`.

```
/template-chat  (template-chat)
  └── ListWorkspaceTemplateChat component (workspaceId from authStore.user.activeWorkspace.id)
        ├── onMounted: templateChatStore.onIndex({ idWorkspace })
        ├── Table with search and pagination
        │
        ├── "Tambah Template" button → ModalFormTemplateChat (modal: 'add')
        │     ├── form: namaTemplate, content, mediaLink (optional FileUpload)
        │     │     ├── FileUpload accepts .jpg .jpeg .png .gif .mp4 up to 5 MB
        │     │     ├── on file select → templateChatStore.onUploadMedia(file)
        │     │     │     └── POST /master/saktiform-media → returns URL
        │     │     └── submit → templateChatStore.onStore({ ..., mediaLink })
        │     └── templateChatStore.onGetTemplateVariable() loads variable picker
        │
        ├── Row action: Edit → ModalFormTemplateChat (modal: 'edit')
        │     ├── templateChatStore.onDetail(id) pre-fills form
        │     ├── existing mediaLink shown in FileUpload preview
        │     └── submit → templateChatStore.onStore({ ..., mediaLink })
        │
        └── Row action: Delete → ConfirmModal → templateChatStore.onDelete(id)
```

---

## 9. Error / Not Found

```
/not-found
  ├── reached when: route doesn't exist, or rolePermission check fails in guard
  ├── renders: error message
  └── "Kembali ke Dashboard" button → router.push({ name: 'beranda' })
```
