# Backend Architecture

This document describes the current backend architecture of the Saktiform AI platform — a **multi-tenant conversational-commerce** Spring Boot monolith (PostgreSQL) where every resource is scoped to a `Workspace` (tenant).

---

## 1. Package Structure

```
com.saktiform.api/
├── configuration/   # Cross-cutting setup
│   ├── SecurityConfig              # SecurityFilterChain, CORS, JWT filter wiring
│   ├── RestSecurityConfiguration   # AuthenticationProvider, BCrypt encoder
│   ├── JwtManager / JwtAuthenticationFilter
│   ├── WebsocketConfiguration      # STOMP /ws, broker /topic
│   ├── MinioConfig, SwaggerConfiguration, WebConfig
├── controller/      # 11 REST controllers — thin, delegate to services
├── entity/          # ~29 JPA entities + enums (Role, statuses)
├── model/           # DTOs / requests / responses, sub-packaged by domain
│   ├── chat/  order/  product/  ...
├── repository/      # Spring Data JPA repos; native SQL + JPQL for tenant queries
├── service/         # Business logic
│   ├── chat/        # ConversationService, ChatMessageService, WhatsApp* services
│   │   └── bot/     # Bot orchestration + LLM providers
│   └── order/       # OrderOrchestrationService, OrderService, OrderEventListener
├── util/            # WhatsappClientHelper, MessageConstructorHelper, InitializerSeeder
└── validators/      # Custom JSR-303 validators
```

**Layering rule:** `controller → service → repository → entity`. Controllers carry no business logic; they receive `workspaceId` as a parameter and pass it down. DTO projection happens at the repository (query) level, not in service code.

---

## 2. Request Flow

### HTTP request lifecycle
```
Client
  → CORS filter
  → JwtAuthenticationFilter (before UsernamePasswordAuthenticationFilter)
       ├─ no/!Bearer header → continue unauthenticated (permitAll endpoints pass)
       └─ Bearer <token> → validate → load UserDetails → set SecurityContext
  → DispatcherServlet → Controller (@RestController)
  → Service (business logic, @Transactional)
  → Repository (Spring Data JPA, workspace-scoped query)
  → Entity / DTO projection
  → RestResponse<T> serialized to JSON
```

All endpoints return `RestResponse<T>`; validation failures return `ErrorResponse` with a list of `ErrorDto`.

### Asynchronous event flows
Two core operations are **event-driven** and run after the DB transaction commits, decoupling side-effects (WhatsApp, WebSocket) from the request thread.

**Incoming WhatsApp message → bot reply**
```
POST /whatsapp/webhook (public)
  → WhatsappMessageHandler: save Chat, update Conversation, publish IncomingChatEvent
  → BotIncomingChatListener  (@Async, @TransactionalEventListener AFTER_COMMIT)
  → BotOrchestratorService.onIncomingChat()
      ├─ isOrderMessage()?            → order confirmation path
      ├─ BotDecisionService.shouldBotReply()  (bot enabled + quota>0 + TEXT)
      ├─ BotDelayManager.debounce()   (per conversationId; cancels pending tasks)
      └─ BotService.handleBotReply()
            → guardrail check → OpenAI LLM reply → decrement bot quota
            → escalate to human if reply blank
```

**Order creation → WhatsApp follow-up**
```
POST /order/create (public) → OrderOrchestrationService
  → publishes OrderCreatedEvent
  → OrderEventListener (@Async, @TransactionalEventListener AFTER_COMMIT)
      → MessageTemplateService builds follow-up
      → WhatsappClientHelper sends message
      → creates/updates Conversation, publishes WebSocket events
```

### Real-time (WebSocket)
STOMP over SockJS at `/ws`, simple broker on `/topic`, 10s heartbeat, 512 KB max message. `ChatEventPublisher` emits `CONVERSATION_CREATED/UPDATED/REMOVED`, `NEW_MESSAGE`, `CONVERSATION_DETAIL_UPDATED` to workspace-scoped topics (`/topic/conversations/{status}/{workspaceId}`) and per-room topics (`/topic/chatroom/{conversationId}`).

---

## 3. Service Layer

Organized by bounded context under `service/`. Key orchestrators:

**Order (`service/order/`)**
- `OrderOrchestrationService` — entry point; `createOrder()` (external source) and `createOrderOnChat()`; publishes `OrderCreatedEvent`; links conversation + contact.
- `OrderService` — CRUD, abandoned-order handling, product sold-count tracking, history logging.
- `OrderEventListener` — `@Async @TransactionalEventListener(AFTER_COMMIT)`; sends follow-up WhatsApp, opens conversation, publishes chat events.

**Chat (`service/chat/`)**
- `ConversationService` — assigned/unassigned listing, filtering (date/agent/keyword/status), state management.
- `ChatMessageService` — save/retrieve/search messages, fetch recent customer text.
- `WhatsappMessageHandler` / `WhatsappService` / `WhatsappBusinessService` — webhook ingestion, conversation sync, instance/device management.

**Bot / AI (`service/chat/bot/`)**
- `BotOrchestratorService` — top-level flow control (order detection, decision, debounce, reply).
- `BotDecisionService` — gating logic (`handleByBot`, quota, message type).
- `BotDelayManager` — per-conversation debouncing on a `ScheduledExecutorService` sized `max(4, cores*2)`.
- `ContextBuilderService` — assembles `ChatContext` (recent history + order system info).
- `OpenAiLlmService` (active) / `GeminiLlmService` (standby) — reply generation + guardrail check; selected by `AiClientFactory` reading `AI_KEY` from `AppConfig`.
- `QdrantVectorService` — RAG vector search, **currently commented out / inactive**.

**Patterns:** event-driven side-effects, `@Async` listeners, factory selection of LLM provider, debouncing to avoid chat flooding, soft escalation to human.

---

## 4. Repository Layer

All repositories extend `JpaRepository<Entity, ID>`. Query strategy mix (approximate):

| Approach | When used |
|---|---|
| Native SQL (`nativeQuery = true`) | Complex filtering, aggregation, window functions, PostgreSQL-specific features (~60%) |
| JPQL (`@Query`) | Constructor projections, joins, conditional logic (~35%) |
| Derived methods (`findBy*`, `existsBy*`) | Simple lookups (~5%) |

**Multi-tenant isolation** — every list/search query filters by workspace, either directly or via join:

```sql
-- Direct workspace column (Produk, Gudang, Account)
WHERE p.idWorkspace = :idWorkspace AND p.isDeleted != TRUE

-- Join through related entity (Order → Produk → workspace)
JOIN produk prod ON ord.id_produk = prod.id
WHERE prod.id_workspace = :idWorkspace
```

**DTO projection at query level** — constructor projections keep mapping out of service code:
```java
@Query("""
    SELECT new com.saktiform.api.model.product.ProdukListDto(
        p.id, p.namaProduk,
        (SELECT MIN(ap.harga) FROM AtributProduk ap WHERE ap.produk = p),
        p.orderCount, p.soldCount)
    FROM Produk p
    WHERE p.idWorkspace = :idWorkspace AND p.isDeleted != TRUE
    """)
Page<ProdukListDto> findAllProdukListDto(@Param("idWorkspace") Long idWorkspace, Pageable pageable);
```

**Conventions:**
- **Pagination** via `Pageable` on all list endpoints; complex native queries supply a separate `countQuery`.
- **Soft deletes** filtered with `isDeleted != TRUE`.
- **Case-insensitive search** with PostgreSQL `ILIKE` (native) / `LOWER(...) LIKE` (JPQL).
- **PostgreSQL-specific SQL** for dashboards — `generate_series`, `FILTER (WHERE ...)`, `STRING_AGG` — returning report view projections (e.g. `TotalOrderReportView`).

---

## 5. Security

JWT-based, stateless. Defined in `SecurityConfig`, `RestSecurityConfiguration`, `JwtManager`, `JwtAuthenticationFilter`.

**Filter chain (`SecurityConfig`)**
- CSRF disabled; session policy `STATELESS`.
- `jwtFilter` inserted **before** `UsernamePasswordAuthenticationFilter`.
- `permitAll` endpoints:
  ```
  /whatsapp/*/webhook, /whatsapp/webhook, /produk/checkout/**, /order/create/**,
  /location/**, /account/login, /media/**, /uploads/**, /files/**, /auth/**,
  /swagger-ui/**, /v3/api-docs/**, /swagger-resources/**, /configuration/**,
  /webjars/**, /ws/**
  ```
- All other requests: `.anyRequest().authenticated()`.

**Token (`JwtManager`)**
- Algorithm **HS256** (`Keys.secretKeyFor(SignatureAlgorithm.HS256)`).
- Expiry **24 hours**.
- Claims: subject = username, issuedAt, expiration (no custom claims).
- `validateToken` checks username match **and** non-expiry.

**Filter (`JwtAuthenticationFilter`)**
- Reads `Authorization: Bearer <token>`; missing/non-Bearer → passes through unauthenticated.
- Loads `UserDetails` via `UserDetailsServiceImpl` (from `AccountRepository`), validates, sets `SecurityContext` with role-derived authority.

**Auth provider (`RestSecurityConfiguration`)**
- `DaoAuthenticationProvider` + `UserDetailsServiceImpl` + `BCryptPasswordEncoder`.
- Role mapping: `Account.role` (`OWNER` / `CUSTOMER_SERVICE` / `ADMIN`) exposed as a `GrantedAuthority`.

> ⚠️ **CORS is configured in two places** — `SecurityConfig` (inline, `allowCredentials(true)`, exposed headers `*`) and `WebConfig` (`WebMvcConfigurer`, `allowCredentials(false)`). These can conflict; ideally consolidate to one source of truth.

---

## 6. Integrations

| Service | Role | Config source | Status |
|---|---|---|---|
| **WhatsApp Multi-Device API** | Send/receive messages, device pairing (`WhatsappClientHelper`, basic auth + `X-Device-Id`) | `WHATSAPP_MULTIDEVICE_API_URL`, creds in `AppConfig` | Active |
| **OpenAI** | LLM replies + guardrail checks (`OpenAiLlmService`, official SDK via `AiClientFactory`) | `AI_KEY` in `AppConfig` | Active |
| **Gemini** | Alternative LLM (`GeminiLlmService`, REST) | `GEMINI_API_KEY` | Present, inactive |
| **Qdrant** | Vector DB for RAG (`knowledge_base`, `user_tendencies`) | `QDRANT_HOST/PORT` | Scaffolded, commented out |
| **MinIO** | Object storage for product/media files (`StorageService`) | `MINIO_URL/ACCESS_KEY/SECRET_KEY` | Active |
| **Checkout frontend** | External checkout UI | `SAKTIFORM_CHECKOUT_URL` | Active |
| **PostgreSQL** | Primary datastore; `ddl-auto=update`, HikariCP (max 20 / min idle 5) | `application.properties` | Active |

Dynamic configuration (AI keys, prompts, bot delay/quota, WhatsApp credentials) lives in the `AppConfig` table rather than static properties, allowing per-deployment tuning without rebuild.

---

## 7. Dependency Graph

High-level wiring of the two orchestration pipelines and shared infrastructure:

```
HTTP / Webhook
   │
   ├── Controllers ──────────────► Services ──────────► Repositories ──► PostgreSQL
   │
   ▼
BotOrchestratorService
   ├─ BotDecisionService
   ├─ BotDelayManager (ScheduledExecutorService)
   ├─ ChatMessageService ──► ChatRepository / ConversationRepository
   ├─ AppConfigService    ──► AppConfigRepository
   └─ BotService
        ├─ ChatService
        ├─ ContextBuilderService ─► ChatMessageService + MessageConstructorHelper
        ├─ OpenAiLlmService ─► AiClientFactory + AppConfigService  ──► [OpenAI API]
        └─ OrderOrchestrationService
                │
OrderOrchestrationService (also entry from OrderController)
   ├─ OrderService ───────► OrderRepository / AbandonedOrderRepository / OrderHistory
   ├─ ConversationService ─► ConversationRepository
   ├─ ProdukService ──────► ProdukRepository
   ├─ ContactRepository
   └─ ApplicationEventPublisher ──► OrderCreatedEvent
                                        │
                                        ▼
OrderEventListener (@Async, AFTER_COMMIT)
   ├─ MessageTemplateService
   ├─ WhatsappClientHelper ──► [WhatsApp Multi-Device API]
   ├─ ConversationService / ChatMessageService
   ├─ StorageService ─► MinioClient ──► [MinIO]
   └─ ChatEventPublisher ──► STOMP /topic ──► WebSocket clients

Cross-cutting:
   SecurityConfig → JwtAuthenticationFilter → JwtManager + UserDetailsServiceImpl → AccountRepository
```

**Key takeaways**
- The bot and order pipelines converge at `OrderOrchestrationService` (orders can originate from chat or from the public checkout).
- `ApplicationEventPublisher` + `@TransactionalEventListener(AFTER_COMMIT)` is the seam that keeps external I/O (WhatsApp, MinIO, WebSocket) off the request transaction.
- `AppConfigService` is a shared dependency for runtime configuration (AI keys, prompts, quotas).
- There is **no thread-local tenant context**; `workspaceId` is threaded explicitly through every layer.