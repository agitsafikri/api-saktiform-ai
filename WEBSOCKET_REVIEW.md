# 🔌 WebSocket Implementation Review - Saktiform API

**Review Date**: 2026-01-05  
**Scope**: Conversation Messaging via WebSocket

---

## 📊 Overall Assessment

| Aspect | Rating | Status |
|--------|--------|--------|
| **Architecture** | 8/10 | ✅ Good |
| **Topic Structure** | 9/10 | ✅ Excellent |
| **Event Types** | 8/10 | ✅ Good |
| **Error Handling** | 5/10 | ⚠️ Needs Improvement |
| **Security** | 4/10 | ⚠️ Needs Improvement |
| **Performance** | 7/10 | ✅ Good |
| **Documentation** | 3/10 | ❌ Poor |

**Overall**: 6.9/10 - **Good foundation, needs improvements**

---

## 🏗️ Architecture Overview

### WebSocket Configuration

**File**: `WebsocketConfiguration.java`

```java
@Configuration
@EnableWebSocketMessageBroker
public class WebsocketConfiguration implements WebSocketMessageBrokerConfigurer {
    
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")  // ⚠️ Security concern
                .withSockJS();
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic");
        config.setApplicationDestinationPrefixes("/app");
    }
}
```

**✅ Strengths**:
- SockJS fallback enabled untuk browser compatibility
- Simple broker untuk development/small scale
- Clean configuration

**⚠️ Issues**:
1. **Security Risk**: `setAllowedOriginPatterns("*")` - Allows all origins (CORS wide open)
2. **Unnecessary Method Overrides**: Banyak method yang hanya call `super` tanpa custom logic
3. **No Message Size Limits**: Tidak ada konfigurasi untuk message size limits
4. **No Heartbeat Configuration**: Tidak ada custom heartbeat settings

---

## 📡 Event Publisher Service

**File**: `ChatEventPublisher.java`

### Topic Structure

Implementasi menggunakan **hierarchical topic structure** yang sangat baik:

```
/topic/conversations/{workspaceId}
├── /topic/conversations/unassigned/{workspaceId}
│   ├── CONVERSATION_CREATED
│   ├── CONVERSATION_UPDATED
│   └── CONVERSATION_REMOVED
│
├── /topic/conversations/assigned/{workspaceId}
│   ├── CONVERSATION_CREATED
│   ├── CONVERSATION_UPDATED
│   └── CONVERSATION_REMOVED
│
└── /topic/chatroom/{conversationId}
    ├── NEW_MESSAGE
    └── CONVERSATION_DETAIL_UPDATED
```

**✅ Excellent Design**:
- Clear separation antara assigned/unassigned conversations
- Workspace-level isolation untuk multi-tenancy
- Conversation-level topics untuk real-time chat
- Consistent event naming

### Event Types

```java
public class ConversationEvent {
    private String type;      // Event type
    private Object data;      // Payload
    private String timestamp; // Timestamp
}
```

**Event Types Supported**:
1. `CONVERSATION_CREATED` - Conversation baru dibuat
2. `CONVERSATION_UPDATED` - Conversation di-update
3. `CONVERSATION_REMOVED` - Conversation dihapus/dipindah
4. `NEW_MESSAGE` - Pesan baru masuk
5. `CONVERSATION_DETAIL_UPDATED` - Detail conversation berubah

**✅ Strengths**:
- Simple dan consistent event structure
- Timestamp included untuk ordering
- Flexible data payload dengan `Object`

**⚠️ Issues**:
1. **No Event ID**: Tidak ada unique ID per event untuk deduplication
2. **No Retry Metadata**: Tidak ada info untuk retry logic
3. **Generic Object**: `Object data` tidak type-safe, bisa menyebabkan serialization issues

---

## 🔄 Message Flow Analysis

### 1. Incoming Message dari WhatsApp

**File**: `WhatsappService.java:210-225`

```java
// Publish new message ke chatroom
publisher.publishNewMessage(conversation.getId(), newChatUpdate, newChatUpdate.getTanggal());

// Publish conversation update berdasarkan status
if (isNewConversation){
    publisher.publishUnassignedConversationCreated(workspace.getId(), 
        newConversationUpdate, newConversationUpdate.getLastMessageTime());
} else {
    if (conversation.getStatus().equals(ConversationStatus.UNASSIGNED.name())){
        publisher.publishUnassignedConversationUpdated(workspace.getId(), 
            newConversationUpdate, newConversationUpdate.getLastMessageTime());
    } else {
        publisher.publishAssignedConversationUpdated(workspace.getId(), 
            newConversationUpdate, newConversationUpdate.getLastMessageTime());
    }
}
```

**Flow**:
```
WhatsApp Webhook
  → handleGenericMessage()
    → Save message to DB
    → Publish to /topic/chatroom/{conversationId} (NEW_MESSAGE)
    → Publish to /topic/conversations/[assigned|unassigned]/{workspaceId}
```

**✅ Strengths**:
- Dual publish: chatroom + conversation list
- Correct status-based routing
- Atomic operation (save + publish)

**⚠️ Issues**:
1. **No Transaction Boundary**: Publish happens sebelum transaction commit
2. **No Error Handling**: Jika publish gagal, tidak ada retry
3. **Potential Race Condition**: isNewConversation flag bisa race dengan concurrent requests

---

### 2. Outgoing Message dari Agent

**File**: `ChatService.java:91-103`

```java
var savedChat = chatMessageService.saveChat(chat);

var conversationUpdatedData = new ConversationUpdatedData();
// ... populate data ...

publisher.publishAssignedConversationUpdated(workspace.getId(), 
    conversationUpdatedData, conversationUpdatedData.getLastMessageTime());
```

**Flow**:
```
Agent sends message
  → messageHandler()
    → Send to WhatsApp API
    → Save to DB
    → Publish to /topic/conversations/assigned/{workspaceId}
```

**⚠️ Issues**:
1. **Missing Chatroom Publish**: Tidak publish ke `/topic/chatroom/{conversationId}`
   - Agent yang mengirim pesan tidak akan melihat pesannya sendiri di chatroom
   - Customer juga tidak akan real-time update jika ada multiple agents
2. **No Delivery Status**: Tidak ada event untuk message delivery status

---

### 3. Conversation Takeover

**File**: `ChatService.java:115-141`

```java
public void takeoverConversation(UUID idConversation, String username){
    // ... update conversation ...
    
    // Remove from unassigned
    publisher.publishUnassignedConversationRemoved(workspace.getId(), removedConv, now);
    
    // Add to assigned
    publisher.publishAssignedConversationCreated(workspace.getId(), conversationUpdatedData, now);
}
```

**Flow**:
```
Agent takes over
  → takeoverConversation()
    → Update DB (status = ASSIGNED, handledBy = agent)
    → Publish REMOVED to /topic/conversations/unassigned/{workspaceId}
    → Publish CREATED to /topic/conversations/assigned/{workspaceId}
```

**✅ Strengths**:
- Proper dual publish untuk move conversation
- Clean state transition

**⚠️ Issues**:
1. **No Chatroom Notification**: Customer tidak tahu ada agent yang takeover
2. **No Agent Notification**: Agent lain tidak tahu conversation sudah di-take

---

### 4. Order Selection

**File**: `ConversationService.java:93-103`

```java
public void selectConversationOrder(ConversationSelectOrder order){
    var conversation = conversationRepository.findById(order.getConversationId())
            .orElseThrow(() -> new RuntimeException("Conversation tidak ditemukan"));
    conversation.setActiveOrderId(order.getOrderId());
    
    var savedConversation = conversationRepository.save(conversation);
    
    var conversationDetail = getConversationDetail(savedConversation.getId());
    publisher.publishConversationDetail(savedConversation.getId(), conversationDetail, now);
}
```

**Flow**:
```
Agent selects order
  → selectConversationOrder()
    → Update activeOrderId
    → Publish to /topic/chatroom/{conversationId} (CONVERSATION_DETAIL_UPDATED)
```

**✅ Strengths**:
- Correct topic untuk chatroom-specific update

---

## 🎯 WebSocket Topics Mapping

### Client Subscription Guide

```javascript
// Frontend should subscribe to these topics:

// 1. For conversation list (unassigned)
stompClient.subscribe('/topic/conversations/unassigned/' + workspaceId, (message) => {
    const event = JSON.parse(message.body);
    // event.type: CONVERSATION_CREATED | CONVERSATION_UPDATED | CONVERSATION_REMOVED
    // event.data: ConversationUpdatedData
});

// 2. For conversation list (assigned to me)
stompClient.subscribe('/topic/conversations/assigned/' + workspaceId, (message) => {
    const event = JSON.parse(message.body);
    // event.type: CONVERSATION_CREATED | CONVERSATION_UPDATED | CONVERSATION_REMOVED
    // event.data: ConversationUpdatedData
});

// 3. For chatroom messages
stompClient.subscribe('/topic/chatroom/' + conversationId, (message) => {
    const event = JSON.parse(message.body);
    // event.type: NEW_MESSAGE | CONVERSATION_DETAIL_UPDATED
    // event.data: ChatListDto | ConversationDetail
});
```

---

## ⚠️ Issues & Recommendations

### 🔴 Critical Issues

#### 1. **Security - CORS Wide Open**

**Problem**:
```java
registry.addEndpoint("/ws")
    .setAllowedOriginPatterns("*")  // ❌ Allows ANY origin
```

**Impact**: 
- Any website bisa connect ke WebSocket
- Potential untuk CSRF attacks
- Data leakage

**Solution**:
```java
@Value("${cors.allowed.origins}")
private String allowedOrigins;

@Override
public void registerStompEndpoints(StompEndpointRegistry registry) {
    registry.addEndpoint("/ws")
            .setAllowedOrigins(allowedOrigins.split(","))
            .withSockJS();
}
```

**application.properties**:
```properties
cors.allowed.origins=${CORS_ALLOWED_ORIGINS:http://localhost:3000,https://yourdomain.com}
```

---

#### 2. **Missing Chatroom Publish untuk Outgoing Messages**

**Problem**: Agent yang mengirim pesan tidak publish ke chatroom topic

**Impact**:
- Agent tidak melihat pesannya sendiri real-time
- Jika ada multiple agents, mereka tidak sinkron
- Customer tidak real-time update

**Solution** - Add to `ChatService.java:91`:
```java
var savedChat = chatMessageService.saveChat(chat);

// ✅ ADD THIS - Publish to chatroom
var newChatUpdate = new ChatListDto(
    savedChat.getId(), 
    savedChat.getType(), 
    savedChat.getPengirim(), 
    savedChat.getPesan(), 
    savedChat.getMedia(), 
    savedChat.getSentAt()
);
publisher.publishNewMessage(conversation.getId(), newChatUpdate, 
    chat.getSentAt().atZone(ZoneId.of("Asia/Jakarta")).format(formatter));

// Existing code - Publish to conversation list
publisher.publishAssignedConversationUpdated(workspace.getId(), 
    conversationUpdatedData, conversationUpdatedData.getLastMessageTime());
```

---

#### 3. **No Transaction Boundary for WebSocket Publish**

**Problem**: WebSocket publish terjadi sebelum database transaction commit

**Impact**:
- Client bisa receive event sebelum data tersimpan di DB
- Jika transaction rollback, client sudah terima event yang invalid
- Race condition antara WebSocket event dan database state

**Current Code** (`WhatsappService.java:104`):
```java
@Transactional
protected void handleGenericMessage(WebhookEnvelope webhook, String port) {
    // ... save to database ...
    chatMessageService.saveChat(chat);
    
    // ❌ Publish sebelum transaction commit
    publisher.publishNewMessage(conversation.getId(), newChatUpdate, timestamp);
}
```

**Solution**: Use `@TransactionalEventListener`

**Create Event Class**:
```java
public class ChatMessageSavedEvent {
    private final UUID conversationId;
    private final ChatListDto chatData;
    private final String timestamp;
    // constructor, getters
}
```

**Publish Event Instead**:
```java
@Transactional
protected void handleGenericMessage(WebhookEnvelope webhook, String port) {
    // ... save to database ...
    var savedChat = chatMessageService.saveChat(chat);
    
    // ✅ Publish application event (will be handled after commit)
    eventPublisher.publishEvent(new ChatMessageSavedEvent(
        conversation.getId(), newChatUpdate, timestamp
    ));
}
```

**Create Listener**:
```java
@Component
public class ChatWebSocketEventListener {
    private final ChatEventPublisher publisher;
    
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleChatMessageSaved(ChatMessageSavedEvent event) {
        publisher.publishNewMessage(
            event.getConversationId(), 
            event.getChatData(), 
            event.getTimestamp()
        );
    }
}
```

---

### 🟡 High Priority Issues

#### 4. **No Event Deduplication**

**Problem**: Tidak ada unique ID per event

**Impact**:
- Client bisa process event yang sama multiple times
- Jika ada network retry, duplicate events

**Solution**:
```java
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ConversationEvent {
    private String eventId;   // ✅ ADD: UUID untuk deduplication
    private String type;
    private Object data;
    private String timestamp;
}
```

**Update Publisher**:
```java
public void publishNewMessage(UUID idConversation, Object data, String timestamp) {
    ConversationEvent event = new ConversationEvent(
            UUID.randomUUID().toString(),  // ✅ Generate unique ID
            "NEW_MESSAGE",
            data,
            timestamp
    );
    messagingTemplate.convertAndSend("/topic/chatroom/" + idConversation, event);
}
```

---

#### 5. **No Error Handling untuk Failed Publish**

**Problem**: Jika `messagingTemplate.convertAndSend()` gagal, tidak ada handling

**Impact**:
- Silent failure
- Client tidak terima update
- Tidak ada logging atau retry

**Solution**:
```java
public void publishNewMessage(UUID idConversation, Object data, String timestamp) {
    try {
        ConversationEvent event = new ConversationEvent(
                UUID.randomUUID().toString(),
                "NEW_MESSAGE",
                data,
                timestamp
        );
        
        messagingTemplate.convertAndSend(
                "/topic/chatroom/" + idConversation,
                event
        );
        
        log.debug("Published NEW_MESSAGE to chatroom {}", idConversation);
        
    } catch (Exception e) {
        log.error("Failed to publish NEW_MESSAGE to chatroom {}: {}", 
                  idConversation, e.getMessage(), e);
        // Optional: Store failed event untuk retry
    }
}
```

---

#### 6. **No Authentication/Authorization**

**Problem**: Tidak ada authentication untuk WebSocket connections

**Impact**:
- Anyone bisa subscribe ke any topic
- Data leakage antar workspace
- Security breach

**Solution**: Implement WebSocket Security

```java
@Configuration
public class WebSocketSecurityConfig {
    
    @Bean
    public AuthorizationManager<Message<?>> messageAuthorizationManager(
            MessageMatcherDelegatingAuthorizationManager.Builder messages) {
        
        messages
            .simpDestMatchers("/topic/conversations/**").authenticated()
            .simpDestMatchers("/topic/chatroom/**").authenticated()
            .anyMessage().denyAll();
            
        return messages.build();
    }
}
```

**Add Interceptor untuk Workspace Validation**:
```java
@Component
public class WebSocketAuthInterceptor implements ChannelInterceptor {
    
    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
        
        if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            String destination = accessor.getDestination();
            Principal user = accessor.getUser();
            
            // Validate user has access to workspace
            if (destination.contains("/conversations/")) {
                Long workspaceId = extractWorkspaceId(destination);
                if (!hasAccessToWorkspace(user, workspaceId)) {
                    throw new AccessDeniedException("No access to workspace");
                }
            }
        }
        
        return message;
    }
}
```

---

### 🟢 Medium Priority Improvements

#### 7. **Add Message Size Limits**

```java
@Override
public void configureWebSocketTransport(WebSocketTransportRegistration registry) {
    registry
        .setMessageSizeLimit(512 * 1024)      // 512KB
        .setSendBufferSizeLimit(1024 * 1024)  // 1MB
        .setSendTimeLimit(20 * 1000);         // 20 seconds
}
```

---

#### 8. **Add Heartbeat Configuration**

```java
@Override
public void configureMessageBroker(MessageBrokerRegistry config) {
    config.enableSimpleBroker("/topic")
          .setHeartbeatValue(new long[]{10000, 10000}); // 10s heartbeat
    config.setApplicationDestinationPrefixes("/app");
}
```

---

#### 9. **Clean Up Unnecessary Method Overrides**

**Remove all methods yang hanya call super**:
```java
// ❌ REMOVE THESE
@Override
public void configureWebSocketTransport(WebSocketTransportRegistration registry) {
    WebSocketMessageBrokerConfigurer.super.configureWebSocketTransport(registry);
}

@Override
public void configureClientInboundChannel(ChannelRegistration registration) {
    WebSocketMessageBrokerConfigurer.super.configureClientInboundChannel(registration);
}
// ... etc
```

**Keep only**:
```java
@Configuration
@EnableWebSocketMessageBroker
public class WebsocketConfiguration implements WebSocketMessageBrokerConfigurer {
    
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // ... implementation
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // ... implementation
    }
    
    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registry) {
        // ... add message size limits
    }
}
```

---

#### 10. **Add Logging**

```java
@Component
public class ChatEventPublisher {
    private static final Logger log = LoggerFactory.getLogger(ChatEventPublisher.class);
    
    private final SimpMessagingTemplate messagingTemplate;
    
    public void publishNewMessage(UUID idConversation, Object data, String timestamp) {
        try {
            ConversationEvent event = new ConversationEvent(
                    UUID.randomUUID().toString(),
                    "NEW_MESSAGE",
                    data,
                    timestamp
            );
            
            messagingTemplate.convertAndSend(
                    "/topic/chatroom/" + idConversation,
                    event
            );
            
            log.debug("Published NEW_MESSAGE to chatroom {}", idConversation);
            
        } catch (Exception e) {
            log.error("Failed to publish NEW_MESSAGE: {}", e.getMessage(), e);
        }
    }
}
```

---

## 📊 Performance Considerations

### Current Setup: Simple Broker

**Pros**:
- ✅ Easy setup
- ✅ Good untuk development
- ✅ No external dependencies

**Cons**:
- ❌ In-memory only (tidak scalable)
- ❌ Single server only
- ❌ Lost messages jika server restart

### Recommendation untuk Production

**Use External Message Broker** (RabbitMQ atau Redis):

```java
@Configuration
@EnableWebSocketMessageBroker
public class WebsocketConfiguration implements WebSocketMessageBrokerConfigurer {
    
    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // ✅ Use RabbitMQ for production
        config.enableStompBrokerRelay("/topic", "/queue")
              .setRelayHost("localhost")
              .setRelayPort(61613)
              .setClientLogin("guest")
              .setClientPasscode("guest");
              
        config.setApplicationDestinationPrefixes("/app");
    }
}
```

**Benefits**:
- ✅ Horizontal scaling (multiple servers)
- ✅ Message persistence
- ✅ Better performance
- ✅ Load balancing

---

## 🎯 Summary & Action Items

### ✅ What's Working Well

1. **Excellent Topic Structure** - Clear hierarchy dan separation
2. **Good Event Types** - Comprehensive coverage untuk use cases
3. **Dual Publishing** - Chatroom + conversation list updates
4. **SockJS Fallback** - Browser compatibility

### ⚠️ Critical Fixes Needed

| Priority | Issue | Impact | Effort |
|----------|-------|--------|--------|
| 🔴 CRITICAL | CORS wide open | Security breach | 15 min |
| 🔴 CRITICAL | No authentication | Data leakage | 2-3 hours |
| 🔴 CRITICAL | Missing chatroom publish (outgoing) | UX broken | 30 min |
| 🔴 CRITICAL | No transaction boundary | Race conditions | 1-2 hours |
| 🟡 HIGH | No error handling | Silent failures | 1 hour |
| 🟡 HIGH | No event deduplication | Duplicate processing | 30 min |

### 📋 Recommended Implementation Order

1. **Immediate** (Today):
   - Fix CORS configuration
   - Add chatroom publish untuk outgoing messages
   - Add error handling & logging

2. **This Week**:
   - Implement transaction boundary dengan @TransactionalEventListener
   - Add event deduplication (eventId)
   - Add WebSocket authentication

3. **Next Week**:
   - Add message size limits
   - Add heartbeat configuration
   - Clean up unnecessary method overrides

4. **Future** (Production):
   - Migrate to external message broker (RabbitMQ/Redis)
   - Add monitoring & metrics
   - Add rate limiting

---

## 📝 Code Quality Score

| Aspect | Score | Notes |
|--------|-------|-------|
| Architecture | 8/10 | Solid foundation |
| Security | 4/10 | Critical issues |
| Error Handling | 5/10 | Needs improvement |
| Performance | 7/10 | Good for small scale |
| Maintainability | 7/10 | Clean code |
| **Overall** | **6.2/10** | **Good start, needs hardening** |

---

**Reviewed by**: AI Code Review Assistant  
**Next Review**: After implementing critical fixes
