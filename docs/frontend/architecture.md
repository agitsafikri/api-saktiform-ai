# Frontend Architecture — Saktiform Dashboard

---

## Folder Structure

```
src/
├── main.ts                     # App entry: bootstraps Pinia, Router, global plugins
├── App.vue                     # Root component: global Alert + RouterView
├── auth.ts                     # Router factory + Pinia setup + navigation guards
├── style.css                   # Global resets and font declarations
│
├── apiConfig/
│   ├── client.ts               # Axios instance factory (token injection, 401 handler)
│   └── method.ts               # HTTP verb helpers + errorHelper (auto-imported)
│
├── assets/
│   ├── fonts/myriad-pro/       # Myriad Pro woff font files + style.css
│   ├── icons/                  # Static SVG icon (feature-check.svg)
│   ├── images/                 # logo.png, logo-full.png, and other static images
│   └── styles/
│       ├── index.scss          # Root stylesheet: imports bases + customs
│       ├── bases/              # Utility class sheets (border, box, button, color,
│       │                       #   display, font, input, object, overflow, position,
│       │                       #   size, spacing, text, alert)
│       └── customs/
│           ├── layouts/        # dashboard.scss, client.scss
│           ├── pages/          # login.scss, setting.scss, formProduk.scss
│           └── *.scss          # card, chip, modal, sidebar, skeleton,
│                               #   spinner, table — component-level styles
│
├── components/
│   ├── common/                 # 32 reusable UI components (globally auto-registered)
│   │   ├── table/              # FieldCustom, PaginationCustom, TableCustom, TheadCustom
│   │   └── *.vue               # Alert, Badge, BaseHighlightCard, ButtonCustom,
│   │                           #   ButtonFile, CarouselCustom, ChartComparison,
│   │                           #   ChipCustom, ConfirmModal, DatePicker,
│   │                           #   DateRangePicker, DropdownCustom, FileUpload,
│   │                           #   InputCustom, Modal, MultipleFileUpload,
│   │                           #   MultipleSelectCustom, OtpInputCustom, RadioButton,
│   │                           #   SelectCustom, SingleOtpInput, StepperCustom,
│   │                           #   SwitchButton, Table, TabsWrapper, TextAreaCustom,
│   │                           #   TimePicker, VerificationOtp
│   └── dashboard/
│       ├── DashboardContent.vue  # Breadcrumb bar + <slot> wrapper used by every page
│       ├── Sidebar.vue           # Left navigation with workspace switcher
│       └── SidebarAction.vue     # Collapsible secondary sidebar panel
│
├── layouts/
│   ├── dashboardLayout.vue     # Sidebar + <RouterView> (all authenticated pages)
│   └── default.vue             # Bare <RouterView> (login, unauthenticated)
│
├── pages/                      # File-based routes — one .vue = one route
│   ├── index.vue               # / (name: beranda) — dashboard KPIs + order chart
│   ├── login.vue               # /login (name: login)
│   ├── not-found.vue           # /not-found (name: not-found)
│   ├── chat/
│   │   ├── index.vue           # /chat (name: chat) — split-panel conversation list
│   │   ├── [id].vue            # /chat/detail-pesanan/:id (name: detail-pesanan)
│   │   ├── tambah-pesanan/     # /chat/tambah-pesanan (name: chat-tambah-pesanan)
│   │   └── edit-pesanan/       # /chat/edit-pesanan (name: chat-edit-pesanan)
│   ├── pesanan/
│   │   ├── index.vue           # /pesanan (name: pesanan)
│   │   └── abandoned-cart.vue  # /pesanan/abandoned-cart (name: abandoned-cart)
│   ├── produk/
│   │   ├── index.vue           # /produk (name: produk)
│   │   ├── [id].vue            # /produk/:id (name: edit-produk)
│   │   └── tambah-produk.vue   # /produk/tambah-produk (name: tambah-produk)
│   ├── template-chat/
│   │   └── index.vue           # /template-chat (name: template-chat) — sidebar shortcut
│   ├── campaign/
│   │   └── index.vue           # /campaign (name: campaign) — Chat > Campaign sub-menu
│   └── pengaturan/
│       ├── index.vue           # /pengaturan (name: pengaturan) — OWNER only
│       ├── user/               # /pengaturan/user
│       ├── whatsapp-business/  # /pengaturan/whatsapp-business
│       ├── model-ai/           # /pengaturan/model-ai
│       ├── disable-provinsi/   # /pengaturan/disable-provinsi — OWNER only
│       └── workspace/
│           ├── index.vue       # /pengaturan/workspace
│           └── [id].vue        # /pengaturan/workspace/:id
│
├── stores/                     # Global Pinia stores (auto-imported)
│   ├── alertStore.ts           # Toast notifications — auto-hides after 3 s
│   ├── headerContentStore.ts   # Page title + breadcrumb array for DashboardContent
│   ├── masterStore.ts          # AI key + Facebook Pixel + Google GTM settings
│   ├── responsiveStore.ts      # isMobile flag (threshold: 800 px)
│   └── sidebarStore.ts         # Sidebar state + role-filtered menu list (persisted)
│
├── functions/                  # Pure utility functions (auto-imported)
│   ├── defaultObject.ts        # fieldInfo default shape for form field state
│   ├── delimiter.ts            # Number/money formatting (Indonesian: Rp, `.` delimiter)
│   ├── formHelper.ts           # Client-side form validation + input constraint helpers
│   ├── formater.ts             # Phone number, snake_case → Sentence Case converters
│   ├── moment.ts               # Moment.js with Indonesian locale; date format helpers
│   ├── sidebarAction.ts        # Window resize listener → responsiveStore.setWidth()
│   └── tableHelper.ts          # setShow / setPage / setFilter / setSort for paginated tables
│
└── modules/                    # Feature modules
    ├── auth/stores/authStore.ts
    ├── chat/
    │   ├── components/         # ChatList, ChatDetail, Dropdown, form/AddOrder
    │   ├── store/              # chatStore, locationStore, orderStore
    │   ├── types.ts            # MessageItem, ChatItem, RoleOption, IConversationOrder
    │   └── utils/useWebSocket.ts
    ├── pesanan/
    │   ├── components/         # listPesanan, ListAbandonedCart, ModalDetailPesanan,
    │   │                       #   ModalEditPesanan, ModalEditAbandonedCart
    │   └── stores/pesananStore.ts
    ├── produk/
    │   ├── components/         # ListProduk, FormProduk
    │   └── stores/produkStore.ts
    └── pengaturan/
        ├── components/ListPengaturan.vue
        ├── user-management/    # ListUserManagement + userStore
        ├── whatsapp-business/  # ListWhatsappBusiness + whatsappStore
        ├── province-exception/ # ListProvinceException + provinceExceptionStore
        └── workspace-management/
            ├── components/     # ListWorkspaceManagement, DetailWorkspace
            ├── stores/workspaceStores.ts
            ├── domain/         # ListWorkspaceDomain, ModalFormDomain, domainStore
            ├── gudang/         # ListWorkspaceGudang, ModalFormGudang,
            │                   #   gudangStore, locationStore
            ├── template-chat/  # ListWorkspaceTemplateChat, ModalFormTemplateChat,
            │                   #   templateChatStore
            └── user/           # ListWorkspaceUser, ModalAddWorkspaceUser
```

---

## Routing Architecture

### Route Generation

Routes are generated at build time by `vite-plugin-pages` scanning `src/pages/**/*.vue`. Every `.vue` file becomes a route automatically — no manual route registration.

### Route Metadata (YAML blocks)

Each page file contains a `<route lang="yaml">` block that declares its route name, layout, auth requirements, role restrictions, and breadcrumb:

```yaml
# Example from src/pages/chat/index.vue
name: chat
meta:
  parent: chat
  layout: dashboardLayout
  requiredAuth: 1
  rolePermission: ['OWNER', 'ADMIN', 'CUSTOMER_SERVICE']
  breadcrumb:
    - name: Chat
      route: null
```

| Meta field | Values | Effect |
|---|---|---|
| `layout` | `dashboardLayout` \| `default` | Which layout wraps the page |
| `requiredAuth` | `1` = must be logged in, `2` = must be logged out | Guard redirects accordingly |
| `rolePermission` | Array of `'OWNER'`, `'ADMIN'`, `'CUSTOMER_SERVICE'` | Guard redirects to `not-found` if role not in list |
| `breadcrumb` | Array of `{ name, route }` | Passed to `headerContentStore` for `DashboardContent` to render |
| `parent` | Route name string | Used by `Sidebar.vue` to highlight the active menu item for child routes |

### Dynamic Routes

Three route segments use dynamic parameters:
- `/chat/detail-pesanan/:id` — order detail view (note: declared in `src/pages/chat/[id].vue` but overrides path in its YAML block)
- `/produk/:id` — product edit form
- `/pengaturan/workspace/:id` — workspace detail

### Navigation Guards

`router.beforeEach` in `src/auth.ts` runs on every navigation:

```
requiredAuth === 1 AND no token  →  redirect to 'login'
requiredAuth === 2 AND token     →  redirect to 'beranda'
rolePermission defined AND user's role not in list  →  redirect to 'not-found'
otherwise  →  set breadcrumb in headerStore, proceed
```

### History Mode

`createWebHistory(import.meta.env.VITE_PATH)` — HTML5 history mode. The server must redirect all 404s to `index.html` (standard SPA requirement).

---

## State Management

### Store Overview

All stores use the **Options API style** of `defineStore`. Two stores are persisted to `localStorage`.

| Store | Persisted | Key | Purpose |
|---|---|---|---|
| `useAuthStore` | Yes | `'auth'` | JWT token, user profile (name, username, role, workspaces), active workspace |
| `useSidebarStore` | Yes | `'sidebar'` | Sidebar open/closed state, role-filtered menu list |
| `useAlertStore` | No | — | Global toast: `message`, `type` (`success`/`danger`), `show` flag; auto-hides after 3 s |
| `useHeaderStore` | No | — | `title`, `breadcrumb` array — read by `DashboardContent` |
| `useMasterStore` | No | — | AI key, Facebook Pixel ID, Google GTM ID (settings for the platform) |
| `useResponsiveStore` | No | — | `isMobile` boolean (evaluated once at store init from `screen.width`) |
| `useChatStore` | No | — | `assignedChats`, `unassignedChats` arrays, pagination state, WebSocket event handler |
| `usePesananStore` | No | — | Paginated order list, abandoned cart list, order status options |
| `useProdukStore` | No | — | Product list, active product form item, product attribute/payment lists |
| `useWorkspaceStore` | No | — | Workspace list, workspace detail, users, warehouses, domain, dashboard metrics |
| `useUserStore` | No | — | User management list (under pengaturan) |
| `useWhatsappStore` | No | — | WhatsApp Business account list |
| `useDomainStore` | No | — | Domain list per workspace |
| `useGudangStore` | No | — | Warehouse (gudang) list per workspace |
| `useTemplateChatStore` | No | — | Chat template list per workspace |
| `useProvinceExceptionStore` | No | — | Blocked provinces list + available provinces for picker |
| `useLocationStore` (chat) | No | — | Province/city/district dropdown data for order address |
| `useLocationStore` (gudang) | No | — | Province/city/district dropdown data for warehouse address |
| `useOrderStore` | No | — | Order detail + order logs for chat-side order view |

### Store Pattern

Each feature store follows a consistent internal shape:

```ts
// init function produces a clean default object (used on reset)
export const initItem = () => ({ ... })
export const initInfo = () => ({ ... })  // parallel structure for field-level validation state

export const useXyzStore = defineStore('xyzStore', {
  state: () => ({ items: [], item: initItem(), info: initInfo() }),
  actions: {
    async onIndex(params)   // paginated list
    async onDetail(id)      // single record
    async onStore(data)     // create
    async onUpdate(data)    // update
    // each action calls alertStore.setAlert() on success and error
  }
})
```

The `chatStore` deviates: it manages two separate paginated lists (`assignedChats`, `unassignedChats`) and has a `handleWebSocketEvent()` action that mutates those lists in response to STOMP events.

### Auto-Import Scope

All stores in `src/stores/` and `src/modules/**/stores/` are globally auto-imported — no `import` statement is needed in any page or component.

---

## API Layer

### Client (`src/apiConfig/client.ts`)

A factory function `generateInstance(baseURL)` creates an Axios instance with:
- `timeout`: 3,600 × 60 ms (effectively no timeout)
- `Content-Type: application/json` default header
- `withCredentials: true`

**Request interceptor** — calls `useAuthStore()` and attaches `Authorization: Bearer <token>` to every outgoing request if a token exists.

**Response interceptor** — on a `401` response, sets `authStore.token = null`. It does not redirect to login — that only happens on the next navigation via the route guard.

The factory is wrapped in a class with a single static method:
```ts
HttpClient.getInstance('api_url')  // returns the instance pointed at VITE_BASE_URL
```

### Methods (`src/apiConfig/method.ts`)

Seven auto-imported functions wrap the Axios instance:

| Function | HTTP method | Notes |
|---|---|---|
| `getData(type, url, params)` | GET | params sent as query string |
| `postData(type, url, data, options?)` | POST | JSON body |
| `patchData(type, url, data)` | PATCH | JSON body |
| `putData(type, url, data)` | PUT | JSON body |
| `destroyData(type, url)` | DELETE | no body |
| `uploadData(type, url, data)` | POST | forces `multipart/form-data` header |
| `downloadFile(type, url, params?)` | GET | `responseType: 'blob'` |

`errorHelper(err)` normalises error responses:
```ts
{ message: error?.message || 'Jaringan Bermasalah', success: false }
```

All store actions follow the same pattern:
```ts
try {
  const response = await getData('api_url', endpoint, params)
  alertStore.setAlert(response.data.message, 'success')
  return response.data
} catch (err) {
  alertStore.setAlert(err.response?.data?.message || 'Jaringan Bermasalah', 'danger')
  return errorHelper(err)
}
```

---

## Authentication Flow

### Login

```
User submits credentials (loginModel — module-level reactive object in authStore)
  → authStore.login(payload)
    → POST /account/login
    → stores { nama, username, role, workspaces, token } in Pinia state
    → persisted to localStorage key 'auth'
    → sidebarStore.setMenus(role) — populates role-filtered menu list
    → router.push({ name: 'beranda' })
```

### Session Persistence

On page reload, `pinia-plugin-persistedstate` rehydrates `authStore` from `localStorage['auth']`. If `token` is non-null, `isLoggedIn` is `true` and the guard passes `requiredAuth: 1` routes.

### Logout

```
authStore.logout()
  → clears user to initUser shape, token = null
  → manually removes 'auth' and 'sidebar' keys from localStorage
```

There is no server-side session invalidation call — logout is purely client-side.

### Guard Logic (summarised)

```
Every route change:
  requiredAuth 1 + !isLoggedIn  →  /login
  requiredAuth 2 + isLoggedIn   →  /beranda
  rolePermission set + role not included  →  /not-found
  no match  →  proceed, update breadcrumb
```

### Role-Based Menu Filtering

`useSidebarStore.setMenus(role)` called at login and on first navigation of each session (via `router.beforeEach` when `sidebarStore.menus.length === 0`). The `menus` array is NOT persisted to localStorage — it is rebuilt from source code each session to stay current.

Chat is a **parent group** with three sub-menus: Inbox (`/chat`), Template Chat (`/template-chat`), Campaign (`/campaign`).

- `OWNER` → 5 top-level items: Dashboard, Produk, Pesanan, Chat (group), Pengaturan
- `ADMIN` → 4 items (Pengaturan excluded)
- `CUSTOMER_SERVICE` → 4 items (Pengaturan excluded)

Parent group highlighting: `isActive = !isOpen && hasActiveChild` — the Chat parent highlights when collapsed with an active child, and is unhighlighted when expanded.

The route-level `rolePermission` guard provides a second layer — even if a user navigates directly to `/pengaturan`, the guard redirects them to `not-found`.

---

## Component Hierarchy

```
App.vue
├── Alert.vue                        (global toast, always mounted)
└── RouterView
    └── [Layout]
        ├── default.vue
        │   └── RouterView           (login page, bare)
        └── dashboardLayout.vue
            ├── Sidebar.vue          (left navigation, workspace switcher, user initials)
            └── RouterView
                └── [Page].vue
                    └── DashboardContent.vue   (breadcrumb bar + <slot>)
                        └── [Module Components]
                            ├── ChatList / ChatDetail
                            ├── ListProduk / FormProduk
                            ├── listPesanan / ListAbandonedCart
                            └── ListPengaturan / workspace sub-components
```

### Component Communication Patterns

- **Parent → child**: props (typed with `defineProps<{...}>()`)
- **Child → parent**: `defineEmits<{...}>()` with typed event payloads
- **Sibling / cross-tree**: Pinia stores
- **`v-model` on components**: pages use `v-model:active-tab`, `v-model:selected-role` etc. for two-way binding with child components

---

## Shared Components (`src/components/common/`)

All components in this directory are globally auto-registered — no import needed anywhere.

### Form Inputs
| Component | Purpose |
|---|---|
| `InputCustom` | Text/password input with label, loading skeleton, prefix/suffix icon slots |
| `TextAreaCustom` | Multi-line text input |
| `SelectCustom` | Single-select dropdown |
| `MultipleSelectCustom` | Multi-select with tag display |
| `RadioButton` | Radio group |
| `SwitchButton` | Toggle switch |
| `DatePicker` | Single date picker |
| `DateRangePicker` | Start + end date range with time support |
| `TimePicker` | Time-only picker |
| `OtpInputCustom` / `SingleOtpInput` | OTP entry fields |

### File Handling
| Component | Purpose |
|---|---|
| `FileUpload` | Single file upload with preview |
| `MultipleFileUpload` | Multiple files with preview |
| `ButtonFile` | Styled file input button |

### Display / Feedback
| Component | Purpose |
|---|---|
| `Alert` | Auto-dismissing toast (reads from `alertStore`) |
| `Badge` | Status badge with `variant` prop |
| `ChipCustom` | Closeable chip/tag |
| `BaseHighlightCard` | KPI metric card (title + value + color) |
| `ChartComparison` | ApexCharts line chart wrapper (series + categories props) |
| `CarouselCustom` | Image carousel |

### Overlays
| Component | Purpose |
|---|---|
| `Modal` | General-purpose modal with `#content` and `#action` slots |
| `ConfirmModal` | Pre-wired confirm/cancel modal |
| `DropdownCustom` | Positioned dropdown panel |

### Navigation / Layout
| Component | Purpose |
|---|---|
| `TabsWrapper` | Tab bar with slot-based panel switching |
| `StepperCustom` | Step indicator for multi-step flows |

### Table System (4 components)
| Component | Purpose |
|---|---|
| `TableCustom` | Full data table with integrated header + pagination |
| `TheadCustom` | Sortable column header row |
| `FieldCustom` | Individual table cell with type-aware rendering |
| `PaginationCustom` | Page number + per-page selector |

### Verification
| Component | Purpose |
|---|---|
| `VerificationOtp` | Full OTP verification flow (input + resend) |

---

## Layout Structure

### `default.vue`

Bare wrapper — only renders `<RouterView />`. Used by `/login`. No sidebar, no breadcrumb.

### `dashboardLayout.vue`

```html
<Sidebar />
<main class="dashboard-layout" :class="{ full: !sidebarStore.isSidebarOpen }">
  <RouterView />
</main>
```

The `full` CSS class expands the main content to fill the full width when the sidebar is collapsed. `Sidebar.vue` handles its own open/closed animation via the `hide` class from `sidebarStore.isSidebarOpen`.

### `DashboardContent.vue` (not a layout but used by all dashboard pages)

Every page inside `dashboardLayout` wraps its content in `<DashboardContent>`, which renders the breadcrumb bar (sourced from `headerContentStore.breadcrumb`) followed by a `<slot />`. The breadcrumb supports both plain route links and parameterised routes.

---

## Page Structure

Every page file follows the same three-block structure:

```
<script setup lang="ts">  — logic
<template>                — markup, always opens with <DashboardContent> for dashboard pages
<style scoped>            — page-specific styles (optional)
<route lang="yaml">       — route metadata
```

### Page Inventory

| Route | Page file | Layout | Auth | Role |
|---|---|---|---|---|
| `/` | `pages/index.vue` | dashboardLayout | 1 | All |
| `/login` | `pages/login.vue` | default | 2 (logged-out only) | — |
| `/not-found` | `pages/not-found.vue` | dashboardLayout | 1 | — |
| `/chat` | `pages/chat/index.vue` | dashboardLayout | 1 | All |
| `/chat/detail-pesanan/:id` | `pages/chat/[id].vue` | dashboardLayout | 1 | All |
| `/chat/tambah-pesanan` | `pages/chat/tambah-pesanan/index.vue` | dashboardLayout | 1 | All |
| `/chat/edit-pesanan` | `pages/chat/edit-pesanan/index.vue` | dashboardLayout | 1 | All |
| `/template-chat` | `pages/template-chat/index.vue` | dashboardLayout | 1 | — |
| `/campaign` | `pages/campaign/index.vue` | dashboardLayout | 1 | — |
| `/pesanan` | `pages/pesanan/index.vue` | dashboardLayout | 1 | — |
| `/pesanan/abandoned-cart` | `pages/pesanan/abandoned-cart.vue` | dashboardLayout | 1 | — |
| `/produk` | `pages/produk/index.vue` | dashboardLayout | 1 | — |
| `/produk/tambah-produk` | `pages/produk/tambah-produk.vue` | dashboardLayout | 1 | — |
| `/produk/:id` | `pages/produk/[id].vue` | dashboardLayout | 1 | — |
| `/pengaturan` | `pages/pengaturan/index.vue` | dashboardLayout | 1 | OWNER |
| `/pengaturan/user` | `pages/pengaturan/user/index.vue` | dashboardLayout | 1 | — |
| `/pengaturan/whatsapp-business` | `pages/pengaturan/whatsapp-business/index.vue` | dashboardLayout | 1 | — |
| `/pengaturan/model-ai` | `pages/pengaturan/model-ai/index.vue` | dashboardLayout | 1 | — |
| `/pengaturan/workspace` | `pages/pengaturan/workspace/index.vue` | dashboardLayout | 1 | — |
| `/pengaturan/workspace/:id` | `pages/pengaturan/workspace/[id].vue` | dashboardLayout | 1 | — |
| `/pengaturan/disable-provinsi` | `pages/pengaturan/disable-provinsi/index.vue` | dashboardLayout | 1 | OWNER |

---

## Utility Modules (`src/functions/`)

All functions are globally auto-imported — no import statement needed in pages or components.

### `delimiter.ts`
- `setDelimiter(value, delimiter='.')` — formats a number with thousands separator
- `removeDelimiter(value, delimiter, decimal)` — strips separators and returns a number
- `setMoneyDelimiter(value, ...)` — formats as `Rp 1.234.567` with configurable precision
- `removeMoneyDelimiter(value, ...)` — parses a formatted currency string back to a number

### `formater.ts`
- `formatPhoneNumber(number)` — converts `628xxx` to `+62 8xxx xxxx` format
- `removeSnakeCase(str)` — replaces underscores with spaces
- `convertSentenceCase(str)` — converts `CUSTOMER_SERVICE` → `Customer Service`

### `formHelper.ts`
- `validateForm(validation, item, error)` — runs rules (required, minLength, maxLength, minValue, maxValue, minItems, maxItems, differentWith, sameWith, moreThanWith) against a form item and writes results into an error object
- `validateArrayForm(validation, items, error)` — applies `validateForm` to each item in an array
- `inputNumberOnly(event)` — key filter: digits only
- `inputNumberAndDotOnly(event)` — key filter: digits and decimal point
- `inputLetterOnly(event)` — key filter: letters only
- `inputNumberLimitation(event, min, max)` — clamps input to a numeric range
- `preventSpaceAndSlash(event)` — blocks space and `/` key

### `moment.ts`
Configures Moment.js with an Indonesian locale (`id`). Exports:
`formatDate`, `formatDate2`, `formatFullDate`, `formatDateTime`, `formatDateTime2`, `formatFullDateTime`, `formatTime`, `formatDateSystem`, `formatDateTimeSystem`

### `tableHelper.ts`
`tableHelper(request, getData)` returns `{ setShow, setPage, setFilter, setSort }`. Each function mutates the shared `request` reactive object and re-fetches data. `setShow`, `setPage`, and `setFilter` all reset to page 1.

### `sidebarAction.ts`
`activeSidebar(router)` attaches a `resize` event listener that calls `responsiveStore.setWidth(window.innerWidth)` on mount. Returns `{ isActive, sidebarStore }`.

### `defaultObject.ts`
Exports `fieldInfo = { type: 'info', message: '' }` — the base shape for a form field's validation state object.

---

## Asset Management

### SCSS Architecture

```
src/assets/styles/index.scss        ← root; imported once in main.ts
├── bases/baseIndex.scss            ← @use of all 14 utility sheets
│   ├── border.scss                 ← border utility classes
│   ├── box.scss                    ← flex/grid helpers
│   ├── button.scss                 ← .btn-primary, .btn-secondary, .btn-outline
│   ├── color.scss                  ← color variables + color utility classes
│   ├── display.scss                ← .d-flex, .d-block, .d-grid, etc.
│   ├── font.scss                   ← .fw-bold, .fz-px-{n}, etc.
│   ├── input.scss                  ← base input styles
│   ├── object.scss                 ← cursor, pointer-events
│   ├── overflow.scss               ← overflow utilities
│   ├── position.scss               ← .position-relative, .position-absolute, etc.
│   ├── size.scss                   ← .w-{n}, .h-{n}, .w-p-{n} (percentage), etc.
│   ├── spacing.scss                ← .m-{n}-{side}, .p-{n}-{side} utilities
│   ├── text.scss                   ← .text-align-center, .text-uppercase, etc.
│   └── alert.scss                  ← .alert-success, .alert-danger
└── customs/customIndex.scss        ← @use of layout, page, and component sheets
    ├── layouts/dashboard.scss      ← .dashboard-layout (main content area)
    ├── layouts/client.scss         ← client-facing layout (checkout pages)
    ├── pages/login.scss            ← .login-page styles
    ├── pages/setting.scss          ← settings page layout
    ├── pages/formProduk.scss       ← product form layout
    ├── card.scss                   ← .card base style
    ├── sidebar.scss                ← .sidebar, .sidebar-content, .hide modifier
    ├── skeleton.scss               ← loading skeleton animation
    ├── modal.scss                  ← .modal overlay + container
    ├── spinner.scss                ← loading spinner
    ├── table.scss                  ← table base styles
    └── chip.scss                   ← .chip component style
```

The styling system uses **utility classes** (atomic CSS approach) defined in `bases/`. Pages and components compose these classes directly on HTML elements rather than writing scoped styles, except for layout-specific rules.

### Fonts

Myriad Pro is loaded from `src/assets/fonts/myriad-pro/style.css` via woff files bundled with the app. The global body font is `'Open Sans'` (loaded externally, declared in `style.css`).

### Static Images

`src/assets/images/` contains: `logo.png` (icon-only), `logo-full.png` (icon + wordmark), `bank-transfer-icon.png`, `cod-icon.png`, `order-success.png`. These are imported directly in Vue components using the `@/` alias.

### Icons

`vue-material-design-icons` is imported per-component using the `icons` alias:
```ts
import cogOutline from 'icons/CogOutline.vue'
```
Icons are not auto-imported — each component imports only what it needs.

---

## Build Process

### Vite Configuration (`vite.config.ts`)

The config is a factory function that receives `mode` and loads env vars via `loadEnv` before returning the config object. This allows env vars to influence the build output directory and base path.

**Plugins (in order):**

1. **`@vitejs/plugin-vue`** — Vue 3 SFC compilation
2. **`vite-plugin-pages`** — scans `src/pages/**/*.vue`, generates route objects from file paths and `<route>` blocks; output consumed as `~pages`
3. **`vite-plugin-vue-layouts`** — wraps generated routes with layout components; output consumed as `virtual:generated-layouts`
4. **`unplugin-auto-import`** — generates `src/auto-imports.d.ts`; auto-imports from:
   - `vue`, `vue-router`, `vue/macros`, `@vueuse/head`, `@vueuse/core`, `pinia`
   - `src/stores/`, `src/functions/`, `src/modules/**/stores/`, `src/apiConfig/`
5. **`unplugin-vue-components`** — generates `src/components.d.ts`; auto-registers from:
   - `src/components/`, `src/modules/**/components/`
6. **`vite-plugin-checker`** — TypeScript type-checking during dev (**currently commented out**)

### Build Output

`pnpm build` runs `vue-tsc -b && vite build`:
1. `vue-tsc -b` — type-checks the full project
2. `vite build` — bundles to `VITE_OUTPUT_DIR` (default: `./dist`)

`emptyOutDir: false` in the config means the output directory is **not cleaned** before each build.

### Path Aliases (resolved by Vite)

| Alias | Resolves to |
|---|---|
| `~/` | `src/` |
| `@/` | `src/assets/` |
| `icons` | `node_modules/vue-material-design-icons/` |

### Auto-Generated Files

Two declaration files are generated at dev/build time and are gitignored:
- `src/auto-imports.d.ts` — TypeScript types for all auto-imported symbols
- `src/components.d.ts` — TypeScript types for all globally registered components
