# 📋 Implementation Plan - Saktiform API Enhancements

**Created**: 2026-01-05  
**Status**: Pending Review  
**Priority**: Medium-High

---

## 🎯 Goal

Meningkatkan kualitas dan fitur Saktiform API berdasarkan hasil code review, dengan fokus pada:
1. Chatbot enhancement (expand rules & product integration)
2. WebSocket transaction boundary (prevent race conditions)
3. Media validation (security & storage management)
4. Custom exception classes (better error handling)

---

## 📊 Current Status

### ✅ Already Fixed (Critical Bugs)
- Chatbot initialization bug (`handleByBot = true`)
- Null pointer exceptions (ChatService & ConversationService)
- Error handling (`fillInStackTrace()` → `getMessage()`)
- Security credentials (environment variables)
- WebSocket quick fixes (event deduplication, logging, chatroom publish)

### ⚠️ Remaining Issues (From Code Review)
- Chatbot rules terlalu sederhana (hanya 3 kondisi)
- Tidak ada product integration untuk chatbot
- Tidak ada auto-escalation logic
- WebSocket publish sebelum transaction commit
- Tidak ada media validation
- Generic exception messages

---

## 🔧 Proposed Enhancements

## 🔧 Proposed Enhancements

### Phase 1: Chat Module Reliability (High Priority)

#### 1.1 WebSocket Transaction Boundary
**Problem**: WebSocket publish terjadi sebelum DB transaction commit (Race Condition check).
**Goal**: Ensuring messages are published reliably only after being saved to the DB.

**Proposed Solution**:
```java
// Create event classes
- ChatMessageSavedEvent
- ConversationUpdatedEvent
- ConversationCreatedEvent

// Create listener
@Component
public class ChatWebSocketEventListener {
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleChatMessageSaved(ChatMessageSavedEvent event) {
        publisher.publishNewMessage(...);
    }
}
```

**Files to Create**:
- `model/event/ChatMessageSavedEvent.java`
- `model/event/ConversationUpdatedEvent.java`
- `model/event/ConversationCreatedEvent.java`
- `service/chat/ChatWebSocketEventListener.java`

**Files to Modify**:
- `WhatsappService.java` - Publish events instead of direct WebSocket
- `ChatService.java` - Publish events instead of direct WebSocket
- `ConversationService.java` - Publish events instead of direct WebSocket

**Estimated Effort**: 3-4 hours

---

### Phase 2: Media Validation (High Priority)

#### 2.1 Add Media Validation
**Problem**: Tidak ada validasi untuk media files dari WhatsApp.

**Proposed Changes**:
```java
// Validation rules:
1. File size limit: 10MB for images, 50MB for videos
2. Allowed types: image/*, video/*, application/pdf
3. Virus scanning (optional)
4. Error handling if download fails
```

**Files to Create**:
- `util/MediaValidator.java` - Validation logic
- `configuration/MediaConfiguration.java` - Size limits config

**Files to Modify**:
- `WhatsappService.java` - Add validation before save
- `application.properties` - Add media config

**Estimated Effort**: 2-3 hours

---

### Phase 3: Custom Exception Classes (Medium Priority)

#### 3.1 Create Custom Exceptions
**Goal**: Better error handling dengan specific exceptions.

**Proposed Exceptions**:
```java
// Business exceptions
- ResourceNotFoundException (conversation, product, order not found)
- InvalidOperationException (invalid state transition)
- ValidationException (data validation failed)

// Integration exceptions
- WhatsappApiException (WhatsApp API errors)
- MediaProcessingException (media upload/download errors)

// WebSocket exceptions
- WebSocketPublishException (publish failed)
```

**Files to Create**:
- `exception/ResourceNotFoundException.java`
- `exception/InvalidOperationException.java`
- `exception/ValidationException.java`
- `exception/WhatsappApiException.java`
- `exception/MediaProcessingException.java`
- `exception/GlobalExceptionHandler.java` - @RestControllerAdvice

**Files to Modify**:
- All service classes - Use specific exceptions
- All controllers - Remove try-catch (handled by GlobalExceptionHandler)

**Estimated Effort**: 4-5 hours

---

### Phase 4: Chatbot Enhancement (Future/Low Priority)

#### 4.1 Expand Chatbot Rules
**Current State**: Hanya 3 rules sederhana di `BotService.java`

**Proposed Changes**:
```java
// Current (3 rules)
- "harga" → info harga
- "halo/hai" → greeting
- default → terima kasih
```

**Files to Modify**:
- `BotService.java`
- Create `BotRuleEngine.java`

#### 4.2 Product Integration
**Goal**: Chatbot bisa query database produk untuk info real-time

#### 4.3 Auto-Escalation Logic
**Goal**: Bot auto-escalate ke human jika tidak bisa handle

---

## 📅 Implementation Timeline

### Week 1: Chatbot Enhancement
- **Day 1-2**: Expand chatbot rules (1.1)
- **Day 3**: Product integration (1.2)
- **Day 4**: Auto-escalation logic (1.3)
- **Day 5**: Testing & bug fixes

### Week 2: WebSocket & Media
- **Day 1-2**: Transaction boundary (2.1)
- **Day 3**: Media validation (3.1)
- **Day 4-5**: Custom exceptions (4.1)

**Total Estimated Effort**: 18-25 hours (~2 weeks)

---

## 🎯 Success Criteria

### Chatbot Enhancement
- [ ] Bot dapat handle minimal 15 different intents
- [ ] Bot dapat query dan return product information
- [ ] Bot auto-escalate dalam 5 scenarios
- [ ] Bot success rate >70% (tidak perlu escalate)

### WebSocket Transaction Boundary
- [ ] No race conditions antara WebSocket dan DB
- [ ] Events published after transaction commit
- [ ] No duplicate events

### Media Validation
- [ ] File size limits enforced
- [ ] Only allowed file types accepted
- [ ] Graceful error handling for invalid files
- [ ] No storage overflow

### Custom Exceptions
- [ ] All exceptions properly typed
- [ ] Consistent error response format
- [ ] Better error messages untuk debugging
- [ ] GlobalExceptionHandler handles all exceptions

---

## 🚨 Risks & Mitigation

### Risk 1: Breaking Changes
**Mitigation**: 
- Maintain backward compatibility
- Add feature flags untuk new features
- Thorough testing before deployment

### Risk 2: Performance Impact
**Mitigation**:
- Monitor chatbot response time
- Add caching untuk product queries
- Optimize database queries

### Risk 3: Chatbot Accuracy
**Mitigation**:
- Start with conservative rules
- Monitor escalation rate
- Iterate based on real usage data

---

## 📝 Testing Plan

### Unit Tests
- [ ] BotRuleEngine - Test all rule patterns
- [ ] BotIntentDetector - Test intent classification
- [ ] MediaValidator - Test validation logic
- [ ] Custom exceptions - Test exception handling

### Integration Tests
- [ ] Chatbot flow - End-to-end conversation
- [ ] WebSocket events - Transaction boundary
- [ ] Media upload - Validation & storage
- [ ] Error handling - Exception propagation

### Manual Tests
- [ ] Send various messages to chatbot
- [ ] Test product queries
- [ ] Test escalation scenarios
- [ ] Upload various media types
- [ ] Trigger various errors

---

## 🔄 Rollback Plan

If issues arise:
1. **Chatbot**: Disable bot handling (`handleByBot = false`)
2. **WebSocket**: Revert to direct publish (remove event listener)
3. **Media**: Disable validation temporarily
4. **Exceptions**: Revert to generic RuntimeException

---

## 📊 Metrics to Track

### Before Implementation
- Chatbot rules: 3
- Chatbot success rate: Unknown
- WebSocket race conditions: Possible
- Media validation: None
- Exception types: 1 (RuntimeException)

### After Implementation (Target)
- Chatbot rules: 15+
- Chatbot success rate: >70%
- WebSocket race conditions: None
- Media validation: 100% coverage
- Exception types: 5+ specific exceptions

---

## 💡 Future Enhancements (Out of Scope)

### Not Included in This Plan
1. **OpenAI Integration** - AI-powered chatbot
2. **Qdrant Vector DB** - Semantic search
3. **External Message Broker** - RabbitMQ/Redis
4. **WebSocket Authentication** - User-based access control
5. **Rate Limiting** - API & WebSocket rate limits
6. **Monitoring & Metrics** - Prometheus/Grafana
7. **Conversation Analytics** - Dashboard & reports

---

## ✅ Pre-Implementation Checklist

- [ ] Review implementation plan dengan team
- [ ] Confirm priority order
- [ ] Allocate development time
- [ ] Setup testing environment
- [ ] Backup current database
- [ ] Create feature branch
- [ ] Update documentation

---

## 📞 Next Steps

1. **Review this plan** - Confirm scope & priorities
2. **Adjust timeline** - Based on team capacity
3. **Start Phase 1** - Chatbot enhancement
4. **Iterate** - Based on feedback

---

**Plan Created by**: AI Code Review Assistant  
**Related Documents**:
- `CODE_REVIEW_REPORT.md` - Full code review
- `BUG_FIXES_APPLIED.md` - Critical fixes done
- `WEBSOCKET_REVIEW.md` - WebSocket analysis
- `WEBSOCKET_FIXES_APPLIED.md` - WebSocket quick fixes
