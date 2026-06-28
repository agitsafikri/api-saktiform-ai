# Saktiform AI — Project Overview

A **multi-tenant conversational-commerce platform** built on **Spring Boot + PostgreSQL**. It pairs WhatsApp-based AI chatbots with full e-commerce order management. Every resource is scoped to a `Workspace`, which is the tenant boundary.

---

## 1. Project Architecture

The system is a layered, event-driven Spring Boot monolith.

- **Layered design**: `controller → service → repository → entity`, with `model` (DTOs) and `util`/`validators` supporting them.
- **Event-driven core**: order creation and incoming chats are processed asynchronously through Spring `ApplicationEvent`s and `@TransactionalEventListener(AFTER_COMMIT)`, so side-effects (WhatsApp messages, WebSocket pushes) fire only after the DB transaction commits.
- **Real-time layer**: STOMP-over-SockJS WebSocket (`/ws`, broker on `/topic`) pushes conversation/message updates to the frontend.
- **Stateless security**: JWT (HS256, 24h expiry) validated by a `OncePerRequestFilter` before `UsernamePasswordAuthenticationFilter`.
- **Schema management**: Hibernate `ddl-auto=update`; `InitializerSeeder` (`@PostConstruct`) seeds superadmin/workspace data on boot.

Three orchestration pipelines define the runtime behavior: the **Bot/AI pipeline**, the **Order pipeline**, and the **Chat/WebSocket pipeline**.

---

## 2. Package Structure

```
com.saktiform.api/
├── configuration/   # SecurityConfig, JwtManager, WebsocketConfiguration,
│                    # MinioConfig, SwaggerConfiguration, WebConfig
├── controller/      # 11 REST controllers
├── entity/          # ~29 JPA entities + enums (Role, statuses)
├── model/           # DTOs grouped: chat/, order/, product/, etc.
├── repository/      # Spring Data JPA repos (native SQL for tenant isolation)
├── service/
│   ├── chat/        # ConversationService, ChatMessageService, WhatsApp* services
│   │   └── bot/     # BotOrchestratorService, BotService, BotDecisionService,
│   │                # BotDelayManager, ContextBuilderService, OpenAiLlmService,
│   │                # GeminiLlmService, AiClientFactory, QdrantVectorService
│   └── order/       # OrderOrchestrationService, OrderService, OrderEventListener
├── util/            # WhatsappClientHelper, MessageConstructorHelper, InitializerSeeder
└── validators/      # Custom JSR-303 validators
```

---

## 3. Database Entities

PostgreSQL, multi-tenant — most tables carry `id_workspace` (or reach `Workspace` transitively). UUIDs are used for high-volume transactional tables; `Long` for master/config data; `Integer` for geographic reference data.

### Tenancy & Users
- **`Workspace`** — top-level tenant; links to `WhatsappBusinessApi` and `Domain`.
- **`Account`** — users; `@ManyToMany` with Workspace via `account_workspace`. Role enum = `OWNER`, `CUSTOMER_SERVICE`, `ADMIN`.
- **`Domain`** — custom domains per workspace.

### Products (aggregate around `Produk`, PK = UUID)
- **`Produk`** → `AtributProduk` (variants/price/weight), `ProdukPembayaran` (payment configs, JSON), `GambarProduk` (images), `FiturProduk` (features), `ProdukTestimoni`, `ProdukFormConfig` (dynamic checkout form fields), `ProdukEkstra` (extras, JSON), `ProdukIklan` (FB Pixel / Google GTM ad tracking).

### Orders
- **`Order`** (UUID) — central transaction; FKs to Produk, AtributProduk, Province/City/District, ProdukPembayaran, Conversation, Contact, and handling Account.
- **`AbandonedOrder`** — mirrors Order for abandoned carts.
- **`OrderHistory`** — audit log per order.
- **`OrderSequence`** — per-date sequential order-code generation.
- **`OrderContact`** — junction of orders ↔ conversations.

### Messaging
- **`Contact`** (phone, workspace-scoped) → **`Conversation`** (thread state: unread count, `handleByBot`, `botQuota`, `chatStatus`, `activeOrderId`) → **`Chat`** (individual messages, self-referencing for replies).
- **`ChatTemplate`**, **`QuickReply`** — message templates.
- **`WhatsappBusinessApi`** — per-instance WhatsApp credentials/device.

### Reference / Logistics
- **`Province`**, **`City`**, **`District`** (Indonesian geo hierarchy), **`Ongkir`** (shipping cost lookup), **`Gudang`** (warehouse).

### Config
- **`AppConfig`** — key/value store holding dynamic settings (AI keys, prompts, bot delay/quota, WhatsApp credentials, Qdrant settings).

**Conventions:** soft delete (`isDeleted`) on Produk/AtributProduk/Gudang; `createdAt`/`updatedAt` audit fields almost everywhere; `FetchType.LAZY` relationships; both mapped object and raw FK id stored.

---

## 4. API Flow

11 controllers, all returning `RestResponse<T>`. Public (no-auth) endpoints include WhatsApp webhooks, checkout, order creation, location lookups, login, and media.

| Domain | Controller | Key endpoints |
|---|---|---|
| Auth/Users | `AccountController` | `POST /account/login` (public), register, list, reset-password |
| Tenancy | `WorkspaceController` | CRUD, account assignment, domain binding, `/dashboard-matrix`, `/dashboard-order` |
| Products | `ProdukController` | CRUD, `/produk/checkout` (public), attributes, payments, copy |
| Orders | `OrderController` | `POST /order/create` (public), update/bulk-update, export Excel, abandoned orders, logs, status |
| Chat | `ChatController` | assigned/unassigned conversations, messages, `send-message`, takeover, quick-chat, add-order |
| WhatsApp | `WhatsappController` | `POST /whatsapp/webhook` (public), register/connect/delete instances |
| Support | `DomainController`, `GudangController`, `LocationController`, `ChatTemplateController`, `MasterController` | domains, warehouses, geo lookups, templates, AI-key & pixel/GTM config |

### Incoming WhatsApp message flow
```
POST /whatsapp/webhook
  → WhatsappMessageHandler: save Chat, update Conversation, publish IncomingChatEvent
  → BotIncomingChatListener (@Async, AFTER_COMMIT)
  → BotOrchestratorService.onIncomingChat()
      → isOrderMessage()? → order confirmation path
      → BotDecisionService.shouldBotReply() (bot enabled + quota>0 + TEXT)
      → BotDelayManager.debounce() (per conversation)
      → BotService.handleBotReply() → guardrail check → LLM reply → decrement quota
                                     → escalate to human if reply blank
```

### Order creation flow
```
POST /order/create → OrderOrchestrationService
  → publishes OrderCreatedEvent
  → OrderEventListener (@Async, AFTER_COMMIT)
      → builds follow-up via MessageTemplateService
      → sends WhatsApp via WhatsappClientHelper
      → creates/updates Conversation, publishes WebSocket events
```

---

## 5. Business Modules

1. **Workspace & Identity** — multi-tenant management, account-workspace membership, role-based access, custom domains.
2. **Product Catalog & Checkout** — products with variants, extras, dynamic forms, payment methods, testimonials, ad-tracking pixels; public checkout endpoints.
3. **Order Management** — order lifecycle, abandoned-cart tracking, per-workspace order sequencing, history/audit, Excel export, shipping-cost (ongkir) computation by region.
4. **Conversational Chat** — WhatsApp conversation threads, agent assignment/takeover, quick replies, templates, real-time WebSocket updates.
5. **AI Bot** — automated replies with guardrails, per-conversation debouncing, bot quota, order-intent detection, human escalation.
6. **Dashboard/Analytics** — workspace matrix & order dashboards.

---

## 6. External Integrations

| Service | Role | Config source | Status |
|---|---|---|---|
| **WhatsApp Multi-Device API** | Send/receive messages, device pairing (`WhatsappClientHelper`, basic auth + `X-Device-Id`) | `WHATSAPP_MULTIDEVICE_API_URL`, credentials in AppConfig | Active |
| **OpenAI** | LLM replies + guardrail checks (`OpenAiLlmService` via official SDK, `AiClientFactory`) | `AI_KEY` in `AppConfig`, model/temp/tokens props | Active |
| **Gemini** | Alternative LLM (`GeminiLlmService`, REST) | `GEMINI_API_KEY` | Present but inactive (standby) |
| **Qdrant** | Vector DB for RAG (`knowledge_base`, `user_tendencies`) | `QDRANT_HOST/PORT` | Scaffolded but commented out (`QdrantVectorService` disabled) |
| **MinIO** | Object storage for product/media files (`StorageService`) | `MINIO_URL/ACCESS_KEY/SECRET_KEY` | Active |
| **Checkout frontend** | External checkout UI | `SAKTIFORM_CHECKOUT_URL` | Active |

---

## 7. Notes & Caveats

A few points where the live code diverges from `CLAUDE.md`, worth keeping in mind:

- **Role enum** is actually `OWNER / CUSTOMER_SERVICE / ADMIN`, not `SUPERADMIN / ADMIN / AGENT`.
- The **three-tier bot fallback** (rule → RAG/Qdrant → LLM) is currently effectively **two-tier**: the **Qdrant RAG layer is commented out**, so the bot runs guardrail → direct OpenAI LLM. RAG infrastructure exists but is dormant.
- **Gemini** is wired but not the active provider.
