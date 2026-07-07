# Project Overview — Saktiform Dashboard

## Project Purpose

Saktiform Dashboard is a multi-role admin panel for managing WhatsApp-based e-commerce operations. It centralises four business workflows into a single interface:

- **Real-time chat** — agents handle customer conversations arriving via WhatsApp Business, with live message streaming via WebSocket
- **Order management** — create, view, edit, and track orders including abandoned carts
- **Product catalogue** — add, edit, and list products
- **Workspace settings** — configure workspaces, users, WhatsApp Business accounts, warehouses, domains, and chat templates

Access to features is gated by three roles: `OWNER`, `ADMIN`, and `CUSTOMER_SERVICE`.

---

## Tech Stack

| Concern | Technology |
|---|---|
| UI framework | Vue 3 (Composition API, `<script setup>`) |
| Language | TypeScript |
| Build tool | Vite 5 |
| State management | Pinia + `pinia-plugin-persistedstate` |
| Routing | Vue Router 4 + `unplugin-vue-router` + `vite-plugin-pages` |
| Layout system | `vite-plugin-vue-layouts` |
| HTTP client | Axios |
| Real-time | STOMP over SockJS (`@stomp/stompjs` + `sockjs-client`) |
| Charts | ApexCharts (`vue3-apexcharts`) |
| Date/time | Moment.js (Indonesian locale) + `@vuepic/vue-datepicker` + `v-calendar` |
| Styling | SCSS (custom utility classes, no CSS framework) |
| Input masking | Maska |
| Auto-imports | `unplugin-auto-import` + `unplugin-vue-components` |
| Icons | `vue-material-design-icons` |

---

## Folder Structure

```
dashboard/
├── index.html                  # HTML entry point
├── vite.config.ts              # Vite + plugin configuration
├── src/
│   ├── main.ts                 # App bootstrap (Pinia, Router, global plugins)
│   ├── App.vue                 # Root component (Alert + RouterView)
│   ├── auth.ts                 # Router setup, Pinia setup, global navigation guards
│   ├── style.css               # Global base CSS
│   ├── apiConfig/
│   │   ├── client.ts           # Axios instance factory (token injection, 401 handler)
│   │   └── method.ts           # HTTP helpers: getData, postData, patchData, putData,
│   │                           #   destroyData, uploadData, downloadFile, errorHelper
│   ├── assets/
│   │   ├── fonts/              # Myriad Pro web fonts
│   │   ├── images/             # Logo and static images
│   │   └── styles/             # SCSS: base utilities + custom component styles
│   ├── components/
│   │   ├── common/             # 32 reusable UI components (inputs, modals, table, charts…)
│   │   └── dashboard/          # Sidebar, SidebarAction, DashboardContent wrapper
│   ├── layouts/
│   │   ├── dashboardLayout.vue # Sidebar + <RouterView> (authenticated pages)
│   │   └── default.vue         # Bare <RouterView> (login, not-found)
│   ├── stores/                 # Global Pinia stores
│   │   ├── alertStore.ts       # Toast/alert state (auto-hides after 3 s)
│   │   ├── headerContentStore.ts # Page title and breadcrumb
│   │   ├── masterStore.ts      # AI key + tracking pixel settings
│   │   ├── responsiveStore.ts  # isMobile flag (threshold: 800 px)
│   │   └── sidebarStore.ts     # Sidebar open state + role-filtered menu list (persisted)
│   ├── functions/              # Pure utility helpers (auto-imported globally)
│   │   ├── defaultObject.ts    # Shared default field shape
│   │   ├── delimiter.ts        # Number / money formatting (Indonesian: . delimiter, Rp prefix)
│   │   ├── formHelper.ts       # Client-side form validation + input constraint helpers
│   │   ├── formater.ts         # Phone number, snake_case, SENTENCE_CASE converters
│   │   ├── moment.ts           # Moment.js configured with Indonesian locale; date formatters
│   │   ├── sidebarAction.ts    # Window resize listener tied to responsiveStore
│   │   └── tableHelper.ts      # Pagination/sort/filter helpers for table request objects
│   ├── pages/                  # File-based routes (one .vue = one route)
│   │   ├── index.vue           # /  → Dashboard with KPI cards + order comparison chart
│   │   ├── login.vue           # /login
│   │   ├── not-found.vue       # /not-found (role-guard redirect target)
│   │   ├── chat/
│   │   │   ├── index.vue           # /chat — conversation list
│   │   │   ├── [id].vue            # /chat/:id — active chat thread
│   │   │   ├── tambah-pesanan/     # /chat/tambah-pesanan — create order from chat
│   │   │   └── edit-pesanan/       # /chat/edit-pesanan — edit order from chat
│   │   ├── pesanan/
│   │   │   ├── index.vue           # /pesanan — orders list
│   │   │   └── abandoned-cart.vue  # /pesanan/abandoned-cart
│   │   ├── produk/
│   │   │   ├── index.vue           # /produk — product list
│   │   │   ├── tambah-produk.vue   # /produk/tambah-produk
│   │   │   └── [id].vue            # /produk/:id — edit product
│   │   └── pengaturan/
│   │       ├── index.vue           # /pengaturan — settings menu (OWNER only)
│   │       ├── user/               # /pengaturan/user
│   │       ├── whatsapp-business/  # /pengaturan/whatsapp-business
│   │       ├── model-ai/           # /pengaturan/model-ai
│   │       └── workspace/          # /pengaturan/workspace + /pengaturan/workspace/:id
│   └── modules/                # Feature modules
│       ├── auth/stores/        # authStore (token, user profile, workspace, persisted)
│       ├── chat/
│       │   ├── components/     # ChatList, ChatDetail, Dropdown, form/AddOrder
│       │   ├── store/          # chatStore, locationStore, orderStore
│       │   ├── types.ts        # MessageItem, ChatItem, RoleOption, IConversationOrder
│       │   └── utils/useWebSocket.ts  # Singleton STOMP client
│       ├── pesanan/
│       │   ├── components/     # listPesanan, ListAbandonedCart, modals
│       │   └── stores/         # pesananStore
│       ├── produk/
│       │   ├── components/     # ListProduk, FormProduk
│       │   └── stores/         # produkStore
│       └── pengaturan/
│           ├── components/     # ListPengaturan
│           ├── user-management/
│           ├── whatsapp-business/
│           └── workspace-management/  # domain, gudang, template-chat, user sub-modules
└── public/
    └── saktiform.svg
```

---

## Build and Run Instructions

> Requires Node.js 18+ and [pnpm](https://pnpm.io/).

```bash
# Install dependencies
pnpm install

# Start dev server → http://localhost:5173
pnpm dev

# Production build (runs vue-tsc then vite build)
pnpm build

# Preview the production build locally
pnpm preview
```

There are no configured test or lint scripts.

---

## Environment Variables

Create a `.env` file in the project root. All three variables are required:

```env
VITE_BASE_URL=https://api.example.com/   # Backend REST API base URL — must end with /
VITE_PATH=/                              # App base path (used by Vue Router history and Vite base)
VITE_OUTPUT_DIR=./dist                   # Build output directory
```

The WebSocket endpoint is derived at runtime as `VITE_BASE_URL + 'ws'` (e.g. `https://api.example.com/ws`) — there is no separate env variable for it.

> `.env` is listed in `.gitignore` and must not be committed.

---

## Main Dependencies

| Package | Purpose |
|---|---|
| `vue` `vue-router` | Core UI framework and routing |
| `pinia` `pinia-plugin-persistedstate` | State management; `auth` and `sidebar` stores persist to `localStorage` |
| `axios` | HTTP client (all requests go through `src/apiConfig/`) |
| `@stomp/stompjs` `sockjs-client` | WebSocket (STOMP protocol) for real-time chat |
| `vue3-apexcharts` `apexcharts` | Dashboard KPI charts |
| `moment` | Date formatting with Indonesian locale |
| `@vuepic/vue-datepicker` `v-calendar` | Date and date-range picker components |
| `maska` | Input masking (phone numbers, etc.) |
| `vue-material-design-icons` | Icon set (imported with the `icons` alias) |
| `unplugin-auto-import` | Auto-imports Vue, Pinia, VueUse, stores, functions, API methods |
| `unplugin-vue-components` | Auto-registers components from `src/components/` and module `components/` dirs |
| `vite-plugin-pages` `unplugin-vue-router` | File-based routing from `src/pages/` |
| `vite-plugin-vue-layouts` | Layout wrapping via `<route>` block `meta.layout` |

---

## Application Entry Point

```
index.html
  └── src/main.ts          ← createApp(App), registers global plugins
        └── src/auth.ts    ← createRouter, createPinia, navigation guards
              └── src/App.vue   ← <Alert /> + <RouterView />
```

`main.ts` bootstraps Pinia (with persistence), Vue Router, ApexCharts, `VueDatePicker`, and `v-calendar` before mounting `#app`.

---

## High-Level Application Flow

1. **Bootstrap** — `main.ts` calls `moduleConfig(app)` from `auth.ts`, which sets up Pinia and the router, then mounts the app.

2. **Navigation guard** (`auth.ts` `router.beforeEach`) — on every route change:
   - `requiredAuth: 1` and no token → redirect to `/login`
   - `requiredAuth: 2` and token present → redirect to `/beranda` (prevents logged-in users hitting `/login`)
   - `rolePermission` defined and user's role not in the list → redirect to `/not-found`
   - Sets breadcrumb via `useHeaderStore`

3. **Layout selection** — `vite-plugin-vue-layouts` reads `meta.layout` from each page's `<route>` block:
   - `dashboardLayout` — renders `<Sidebar>` + the page content
   - `default` (or omitted) — renders the page without a sidebar

4. **Sidebar menus** — populated at login time by `useSidebarStore.setMenus(role)`. `ADMIN` and `CUSTOMER_SERVICE` have the `Pengaturan` entry filtered out.

5. **API calls** — all HTTP calls use the helpers from `src/apiConfig/method.ts` (`getData`, `postData`, etc.). The Axios interceptor in `client.ts` attaches the Bearer token from `authStore` on every request and clears it on a `401` response.

6. **Real-time chat** — on entering `/chat`, `useWebSocket().connectGlobal(workspaceId, cb)` opens a singleton STOMP connection and subscribes to the workspace's unassigned and assigned conversation topics. Opening a specific chat calls `subscribeToChat(conversationId, cb)` on the same client.

7. **Workspace switching** — the sidebar header exposes a `<select>` that calls `authStore.setActiveWorkspace(workspace)`, resets the active menu, and redirects to `/beranda`.

---

## Current Project Status

- The application is functional and connected to a live backend at `https://api.saktiform.com/`.
- The `vite-plugin-checker` (TypeScript type checking during dev) is commented out in `vite.config.ts`.
- PWA service worker registration is present but commented out in `main.ts`.
- There are no automated tests configured.
- There is no lint script configured (ESLint and Prettier are installed but only run manually).

---

## Known Limitations

- **No test suite** — there are no unit, integration, or E2E tests. ESLint and Prettier are installed but have no npm script entry points.
- **`any` used widely** — many store actions, component props, and API responses are typed as `any`, reducing type safety.
- **Singleton WebSocket** — `stompClient` is a module-level variable. Navigating away from chat and back without an explicit `disconnect()` call risks duplicate subscriptions.
- **`screen.width` at store init** — `responsiveStore` reads `screen.width` at module evaluation time (not reactive to resize). The resize listener is attached in `sidebarAction.ts` but that composable must be explicitly called by a component.
- **Hardcoded 401 token clear** — the Axios response interceptor clears the token on any 401 but does not redirect to `/login`. The redirect only happens the next time the user navigates.
- **`emptyOutDir: false` in build config** — the production build does not clean `dist/` before building, which can leave stale assets between builds.
- **No error boundary** — there is no global Vue error handler or fallback UI for runtime errors beyond the `useAlertStore` toast.
- **Mobile layout incomplete** — `responsiveStore.isMobile` is set and the dashboard home page has responsive grid breakpoints, but most inner pages do not adapt for small screens.
