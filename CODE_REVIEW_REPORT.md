# 📋 Code Review Report - Saktiform API

## 🎯 Ringkasan Eksekutif

Proyek ini adalah Spring Boot API untuk backend aplikasi yang mencakup:
- ✅ **Order Management** - Sudah lengkap dan berfungsi
- ✅ **Product Management** - Sudah lengkap dan berfungsi  
- ⚠️ **Customer Service Chat Room** - Implementasi dasar ada, tapi ada beberapa masalah
- ⚠️ **Chatbot Customer Service** - Implementasi sangat sederhana, perlu pengembangan

---

## 🔍 Temuan Masalah Kritis

### 1. **Chatbot - Inisialisasi `handleByBot` Tidak Ada**

> [!CAUTION]
> **Masalah Kritis**: Field `handleByBot` di entity `Conversation` tidak pernah diinisialisasi saat conversation baru dibuat.

**Lokasi**: `WhatsappService.java:129-137`

**Kode Bermasalah**:
```java
if(conversation == null){
    isNewConversation = true;
    conversation = new Conversation();
    conversation.setStatus(ConversationStatus.UNASSIGNED.name());
    conversation.setIdContact(contact.getId());
    conversation.setCreatedAt(Instant.now());
    // ❌ TIDAK ADA: conversation.setHandleByBot(true);
}
```

**Dampak**:
- Chatbot tidak akan pernah merespon pesan karena `handleByBot` bernilai `null`
- Di `BotDecisionServiceImpl.java:23`, ada pengecekan `if (!conversation.getHandleByBot())` yang akan throw `NullPointerException`

**Solusi**:
```java
if(conversation == null){
    isNewConversation = true;
    conversation = new Conversation();
    conversation.setStatus(ConversationStatus.UNASSIGNED.name());
    conversation.setIdContact(contact.getId());
    conversation.setCreatedAt(Instant.now());
    conversation.setHandleByBot(true); // ✅ TAMBAHKAN INI
}
```

---

### 2. **Null Pointer Exception Risks**

> [!WARNING]
> Banyak kode yang menggunakan `.get()` tanpa pengecekan `Optional.isPresent()` terlebih dahulu.

**Contoh Lokasi Bermasalah**:

1. **ChatService.java:49**
```java
var conversation = conversationRepository.findById(data.getConversationId()).get();
// ❌ Tidak ada pengecekan jika conversation tidak ditemukan
```

2. **ChatService.java:117**
```java
var account = accountRepository.findByUsername(username);
conversation.setHandledBy(account.get().getId());
// ❌ Tidak ada pengecekan jika account tidak ditemukan
```

3. **ConversationService.java:76**
```java
var conversation = conversationRepository.findById(idConversation).get();
// ❌ Tidak ada pengecekan
```

**Solusi yang Disarankan**:
```java
// ✅ Cara yang benar
var conversation = conversationRepository.findById(data.getConversationId())
    .orElseThrow(() -> new RuntimeException("Conversation tidak ditemukan"));
```

---

### 3. **Chatbot - Implementasi Terlalu Sederhana**

> [!IMPORTANT]
> Chatbot saat ini hanya menggunakan rule-based sederhana dengan 3 kondisi saja.

**Lokasi**: `BotService.java:37-50`

**Kode Saat Ini**:
```java
private String ruleBasedReply(ChatContext context) {
    String text = context.getUserMessage().toLowerCase();
    
    if (text.contains("harga")) {
        return "Untuk info harga, boleh sebutkan nama produknya ya kak 🙂";
    }
    
    if (text.contains("halo") || text.contains("hai")) {
        return "Halo kak 👋 Ada yang bisa kami bantu?";
    }
    
    return "Terima kasih kak, kami bantu cek dulu ya 🙏";
}
```

**Kekurangan**:
- Hanya 3 kondisi sederhana
- Tidak ada integrasi dengan database produk
- Tidak ada context awareness (tidak mengingat percakapan sebelumnya)
- Tidak ada fallback ke admin jika bot tidak bisa handle
- Dependency OpenAI sudah ada di `pom.xml` tapi tidak digunakan

**Rekomendasi Pengembangan**:
1. Tambahkan lebih banyak rule untuk FAQ umum
2. Integrasikan dengan database produk untuk info harga otomatis
3. Implementasikan escalation ke admin jika bot tidak bisa handle
4. Pertimbangkan menggunakan OpenAI API yang sudah ada di dependencies
5. Tambahkan sentiment analysis untuk deteksi customer yang frustrated

---

### 4. **Error Handling Tidak Konsisten**

> [!WARNING]
> Error handling menggunakan `e.fillInStackTrace().getMessage()` yang tidak informatif.

**Contoh di Semua Controller**:
```java
catch (Exception e) {
    e.printStackTrace();
    rest.setSuccess(false);
    rest.setMessage(e.fillInStackTrace().getMessage()); // ❌ Tidak informatif
    rest.setData(null);
    return ResponseEntity.badRequest().body(rest);
}
```

**Masalah**:
- `fillInStackTrace()` tidak mengembalikan message yang berguna
- Seharusnya hanya `e.getMessage()`
- Atau lebih baik lagi, gunakan custom exception dengan message yang jelas

**Solusi**:
```java
catch (Exception e) {
    log.error("Error saat memproses request", e); // ✅ Gunakan logger
    rest.setSuccess(false);
    rest.setMessage(e.getMessage()); // ✅ Langsung getMessage()
    rest.setData(null);
    return ResponseEntity.badRequest().body(rest);
}
```

---

### 5. **Security - Credentials di application.properties**

> [!CAUTION]
> **Security Risk**: Credentials hardcoded di application.properties yang di-commit ke repository.

**Lokasi**: `application.properties`

```properties
# ❌ JANGAN COMMIT CREDENTIALS!
spring.datasource.username=admin
spring.datasource.password=Password1234

whatsapp.api.username=admin
whatsapp.api.password=admin

app.superadmin.username=superadmin
app.superadmin.password=PasswordAdmin1234
```

**Solusi**:
1. Pindahkan ke environment variables
2. Gunakan `application-local.properties` yang di-gitignore
3. Atau gunakan Spring Cloud Config / Vault untuk production

**Contoh Implementasi**:
```properties
# application.properties
spring.datasource.username=${DB_USERNAME}
spring.datasource.password=${DB_PASSWORD}

whatsapp.api.username=${WHATSAPP_USERNAME}
whatsapp.api.password=${WHATSAPP_PASSWORD}

app.superadmin.username=${SUPERADMIN_USERNAME}
app.superadmin.password=${SUPERADMIN_PASSWORD}
```

---

### 6. **WebSocket Configuration Tidak Lengkap**

> [!NOTE]
> WebSocket sudah dikonfigurasi tapi ada method override yang tidak perlu.

**Lokasi**: `WebsocketConfiguration.java`

**Masalah**:
- Banyak method override yang hanya memanggil `super` tanpa implementasi custom
- Ini membuat kode tidak clean dan membingungkan

**Kode yang Tidak Perlu**:
```java
@Override
public void configureWebSocketTransport(WebSocketTransportRegistration registry) {
    WebSocketMessageBrokerConfigurer.super.configureWebSocketTransport(registry);
}
// ❌ Method ini tidak perlu di-override jika tidak ada custom logic
```

**Solusi**: Hapus semua method override yang hanya memanggil super. Cukup sisakan:
```java
@Configuration
@EnableWebSocketMessageBroker
public class WebsocketConfiguration implements WebSocketMessageBrokerConfigurer {
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")
                .withSockJS();
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic");
        config.setApplicationDestinationPrefixes("/app");
    }
}
```

---

### 7. **Missing Validation di Chat Module**

> [!WARNING]
> Tidak ada validasi untuk media files yang di-upload via WhatsApp.

**Lokasi**: `WhatsappService.java:150-202`

**Masalah**:
- Tidak ada validasi ukuran file
- Tidak ada validasi tipe file
- Tidak ada error handling jika download media gagal
- Bisa menyebabkan storage penuh atau security issue

**Contoh Kode Bermasalah**:
```java
if (webhook.getImage() != null) {
    var mediaUrl = mediaBaseUrl+webhook.getImage().get("media_path").asText();
    var mediaType = webhook.getImage().get("mime_type").asText();
    MediaResult mediaResult = mediaHelper.saveMediaFromUrl(mediaUrl, mediaType, msg.get("id").asText());
    // ❌ Tidak ada validasi atau error handling
    chat.setMedia(mediaResult.publicUrl());
}
```

**Solusi yang Disarankan**:
```java
if (webhook.getImage() != null) {
    try {
        var mediaUrl = mediaBaseUrl+webhook.getImage().get("media_path").asText();
        var mediaType = webhook.getImage().get("mime_type").asText();
        
        // ✅ Validasi tipe file
        if (!isAllowedMediaType(mediaType)) {
            log.warn("Media type not allowed: {}", mediaType);
            return;
        }
        
        MediaResult mediaResult = mediaHelper.saveMediaFromUrl(mediaUrl, mediaType, msg.get("id").asText());
        
        // ✅ Validasi ukuran file
        if (mediaResult.fileSize() > MAX_FILE_SIZE) {
            log.warn("File too large: {} bytes", mediaResult.fileSize());
            return;
        }
        
        chat.setMedia(mediaResult.publicUrl());
    } catch (Exception e) {
        log.error("Failed to save media", e);
        // Handle gracefully
    }
}
```

---

### 8. **Conversation Status Management**

> [!IMPORTANT]
> Tidak ada mekanisme untuk mengubah conversation dari bot ke human agent secara otomatis.

**Yang Ada Saat Ini**:
- `takeoverConversation()` - untuk agent mengambil alih conversation secara manual
- `handleByBot` flag di entity

**Yang Kurang**:
- Tidak ada endpoint untuk "return to bot"
- Tidak ada auto-escalation jika bot tidak bisa handle
- Tidak ada tracking berapa lama conversation di-handle bot vs human

**Rekomendasi**:
1. Tambahkan endpoint `POST /conversation/{id}/return-to-bot`
2. Implementasikan auto-escalation di `BotDecisionService`
3. Tambahkan field `handledByBotAt` dan `handledByHumanAt` untuk analytics

**Contoh Implementasi Auto-Escalation**:
```java
@Override
public Boolean shouldBotReply(Chat chat) {
    var conversation = conversationService.findById(chat.getIdConversation());
    
    if (!conversation.getHandleByBot()) {
        return false;
    }
    
    // ✅ Auto-escalate jika customer mengirim pesan non-text
    if (!"TEXT".equalsIgnoreCase(chat.getType())) {
        escalateToHuman(conversation, "Customer mengirim media");
        return false;
    }
    
    // ✅ Auto-escalate jika customer frustrated
    if (isFrustratedMessage(chat.getPesan())) {
        escalateToHuman(conversation, "Customer terdeteksi frustrated");
        return false;
    }
    
    // ✅ Auto-escalate jika sudah terlalu banyak pesan tanpa resolusi
    int messageCount = chatMessageService.countRecentMessages(conversation.getId());
    if (messageCount > 10) {
        escalateToHuman(conversation, "Terlalu banyak pesan tanpa resolusi");
        return false;
    }
    
    return true;
}

private void escalateToHuman(Conversation conversation, String reason) {
    conversation.setHandleByBot(false);
    conversation.setStatus("UNASSIGNED");
    conversationService.saveConversation(conversation);
    log.info("Escalated conversation {} to human: {}", conversation.getId(), reason);
}
```

---

## 📊 Analisis Per Modul

### ✅ Order Management Module
**Status**: **Lengkap dan Berfungsi Baik**

**File Terkait**:
- `OrderController.java`
- `OrderService.java`
- Entity: `Order.java`, `OrderHistory.java`, `AbandonedOrder.java`

**Fitur yang Sudah Ada**:
- ✅ List orders dengan filtering lengkap (provinsi, kota, status, tanggal, dll)
- ✅ Export to Excel
- ✅ Abandoned order tracking
- ✅ Order creation dengan validasi
- ✅ Order update
- ✅ Order detail
- ✅ Order logs/history

**Catatan Kecil**:
- Error handling bisa diperbaiki (lihat poin #4)
- Validasi sudah ada dengan `@Valid`

---

### ✅ Product Management Module
**Status**: **Lengkap dan Berfungsi Baik**

**File Terkait**:
- `ProdukController.java`
- `ProdukService.java`
- Entity: `Produk.java`, `AtributProduk.java`, `GambarProduk.java`, dll

**Fitur yang Sudah Ada**:
- ✅ Add product dengan validasi
- ✅ List products dengan pagination dan search
- ✅ Upload file/media
- ✅ Product detail
- ✅ Product attributes
- ✅ Payment methods per product
- ✅ Checkout page data
- ✅ Delete products (batch)
- ✅ Copy product

**Catatan Kecil**:
- File upload validation bisa ditambahkan
- Error handling bisa diperbaiki

---

### ⚠️ Customer Service Chat Room Module
**Status**: **Implementasi Dasar Ada, Perlu Perbaikan**

**File Terkait**:
- `ChatController.java`
- `ChatService.java`
- `WhatsappService.java`
- `ConversationService.java`
- `WebsocketConfiguration.java`

**Fitur yang Sudah Ada**:
- ✅ WebSocket configuration untuk real-time updates
- ✅ WhatsApp webhook integration
- ✅ Conversation management (assigned/unassigned)
- ✅ Message sending (text, image, video, document, audio)
- ✅ Conversation takeover oleh agent
- ✅ Quick reply templates
- ✅ Message templates dengan parameter

**Masalah yang Ditemukan**:
- ❌ Null pointer risks (lihat poin #2)
- ❌ Media validation kurang (lihat poin #7)
- ❌ Error handling tidak konsisten (lihat poin #4)
- ⚠️ Tidak ada rate limiting untuk webhook
- ⚠️ Tidak ada message retry mechanism jika gagal kirim

**Yang Perlu Ditambahkan**:
1. Message delivery status tracking (sent, delivered, read)
2. Message retry mechanism
3. Rate limiting untuk webhook
4. Conversation archiving
5. Search messages dalam conversation

---

### ⚠️ Chatbot Customer Service Module
**Status**: **Implementasi Sangat Sederhana, Perlu Pengembangan Besar**

**File Terkait**:
- `BotService.java`
- `BotOrchestratorService.java`
- `BotDecisionServiceImpl.java`
- `BotIncomingChatListener.java`
- `ContextBuilderService.java`
- `BotDelayCalculator.java`
- `BotDelayManager.java`

**Fitur yang Sudah Ada**:
- ✅ Event-driven architecture dengan `@TransactionalEventListener`
- ✅ Async processing dengan `@Async`
- ✅ Debouncing mechanism untuk menghindari multiple replies
- ✅ Context building dari 5 pesan terakhir
- ✅ Delay calculator berdasarkan panjang pesan
- ✅ Rule-based reply (sangat sederhana)

**Masalah Kritis**:
- ❌ `handleByBot` tidak diinisialisasi (lihat poin #1) - **BLOCKER**
- ❌ Rule-based reply terlalu sederhana (lihat poin #3)
- ❌ Tidak ada escalation ke human agent
- ❌ OpenAI dependency ada tapi tidak digunakan

**Arsitektur Bot yang Sudah Bagus**:
```
Incoming Message 
  → BotIncomingChatListener (@Async)
    → BotOrchestratorService
      → BotDecisionService (cek apakah bot harus reply)
        → BotDelayManager (debouncing)
          → BotService (generate reply)
            → ChatService (send message)
```

**Yang Perlu Ditambahkan**:
1. **Fix critical bug**: Inisialisasi `handleByBot = true`
2. **Expand rules**: Tambah lebih banyak pattern matching
3. **Product integration**: Query database untuk info produk
4. **Intent detection**: Deteksi intent customer (tanya harga, komplain, tracking, dll)
5. **Escalation logic**: Auto-escalate jika bot tidak confident
6. **Analytics**: Track bot success rate
7. **Admin dashboard**: Untuk training bot dengan conversation history
8. **OpenAI integration**: Untuk fallback jika rule-based tidak match

---

## 🔧 Dependencies yang Sudah Ada Tapi Belum Digunakan

### 1. OpenAI GPT-3 Java Client
```xml
<dependency>
    <groupId>com.theokanning.openai-gpt3-java</groupId>
    <artifactId>service</artifactId>
    <version>0.18.2</version>
</dependency>
```
**Status**: ❌ Tidak digunakan  
**Rekomendasi**: Gunakan untuk chatbot yang lebih intelligent

**Contoh Implementasi**:
```java
@Service
public class OpenAIBotService {
    private final OpenAiService openAiService;
    
    public OpenAIBotService(@Value("${openai.api.key}") String apiKey) {
        this.openAiService = new OpenAiService(apiKey);
    }
    
    public String getAIResponse(String userMessage, String context) {
        CompletionRequest request = CompletionRequest.builder()
            .model("gpt-3.5-turbo")
            .prompt("Context: " + context + "\nUser: " + userMessage + "\nBot:")
            .maxTokens(150)
            .build();
            
        return openAiService.createCompletion(request)
            .getChoices().get(0).getText();
    }
}
```

### 2. Qdrant Vector Database
```xml
<dependency>
    <groupId>io.qdrant</groupId>
    <artifactId>client</artifactId>
    <version>1.11.0</version>
</dependency>
```
**Status**: ❌ Tidak digunakan  
**Rekomendasi**: Bisa digunakan untuk semantic search pada product catalog atau FAQ

### 3. Spring WebFlux (Commented Out)
```xml
<!-- <dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-webflux</artifactId>
</dependency> -->
```
**Status**: ❌ Di-comment  
**Rekomendasi**: Tidak perlu jika tidak ada use case reactive programming

---

## 📝 Rekomendasi Prioritas Perbaikan

### 🔴 **CRITICAL (Harus Segera Diperbaiki)**

#### 1. Fix chatbot initialization bug
**File**: `WhatsappService.java` line 136  
**Effort**: 5 menit  
**Action**: 
```java
conversation.setHandleByBot(true);
```

#### 2. Fix null pointer exceptions
**File**: Semua service files  
**Effort**: 2-3 jam  
**Action**: Ganti semua `.get()` dengan `.orElseThrow()`

**Contoh**:
```java
// ❌ Sebelum
var conversation = conversationRepository.findById(id).get();

// ✅ Sesudah
var conversation = conversationRepository.findById(id)
    .orElseThrow(() -> new ResourceNotFoundException("Conversation tidak ditemukan"));
```

#### 3. Move credentials to environment variables
**File**: `application.properties`  
**Effort**: 30 menit  
**Action**: 
1. Buat file `.env.example`:
```properties
DB_USERNAME=your_db_username
DB_PASSWORD=your_db_password
WHATSAPP_USERNAME=your_whatsapp_username
WHATSAPP_PASSWORD=your_whatsapp_password
SUPERADMIN_USERNAME=your_admin_username
SUPERADMIN_PASSWORD=your_admin_password
```

2. Update `application.properties`:
```properties
spring.datasource.username=${DB_USERNAME}
spring.datasource.password=${DB_PASSWORD}
```

3. Tambahkan `.env` ke `.gitignore`

---

### 🟡 **HIGH (Penting untuk Production)**

#### 4. Improve error handling
**File**: Semua controllers  
**Effort**: 3-4 jam  
**Action**: 
1. Ganti `e.fillInStackTrace().getMessage()` dengan `e.getMessage()`
2. Tambahkan proper logging dengan SLF4J
3. Buat custom exception classes

**Contoh**:
```java
// Custom Exception
public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String message) {
        super(message);
    }
}

// Controller
@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<?> handleNotFound(ResourceNotFoundException e) {
        log.error("Resource not found: {}", e.getMessage());
        RestResponse response = new RestResponse();
        response.setSuccess(false);
        response.setMessage(e.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }
}
```

#### 5. Add media validation
**File**: `WhatsappService.java`  
**Effort**: 2-3 jam  
**Action**: Validasi file size, type, dan error handling

#### 6. Add chatbot escalation logic
**File**: `BotDecisionService.java`  
**Effort**: 4-5 jam  
**Action**: Implementasikan auto-escalate ke human (lihat contoh di poin #8)

---

### 🟢 **MEDIUM (Enhancement)**

#### 7. Expand chatbot rules
**File**: `BotService.java`  
**Effort**: 1-2 hari  
**Action**: 
- Tambah pattern matching untuk FAQ
- Integrasi dengan database produk
- Implementasi intent detection

**Contoh**:
```java
private String ruleBasedReply(ChatContext context) {
    String text = context.getUserMessage().toLowerCase();
    
    // Greeting
    if (text.matches(".*(halo|hai|hello|hi|selamat).*")) {
        return "Halo kak 👋 Ada yang bisa kami bantu?";
    }
    
    // Tanya harga
    if (text.matches(".*(harga|berapa|price|biaya).*")) {
        String productName = extractProductName(text);
        if (productName != null) {
            return getProductPrice(productName);
        }
        return "Untuk info harga, boleh sebutkan nama produknya ya kak 🙂";
    }
    
    // Tracking order
    if (text.matches(".*(track|lacak|status|pesanan|order).*")) {
        return "Untuk cek status pesanan, bisa kasih tau nomor ordernya kak?";
    }
    
    // Komplain
    if (text.matches(".*(komplain|masalah|rusak|cacat|kecewa).*")) {
        escalateToHuman(context.getConversationId(), "Customer komplain");
        return "Mohon maaf atas ketidaknyamanannya kak 🙏 Saya hubungkan dengan tim kami ya";
    }
    
    // Default
    return "Terima kasih kak, kami bantu cek dulu ya 🙏";
}
```

#### 8. Clean up WebSocket configuration
**File**: `WebsocketConfiguration.java`  
**Effort**: 15 menit  
**Action**: Hapus method override yang tidak perlu

#### 9. Add conversation analytics
**Effort**: 1-2 hari  
**Action**: 
- Track bot vs human handling time
- Success rate
- Customer satisfaction
- Response time

---

### 🔵 **LOW (Nice to Have)**

#### 10. Integrate OpenAI for advanced chatbot
**Effort**: 3-5 hari  
**Action**: Implementasi OpenAI API untuk fallback

#### 11. Add message delivery tracking
**Effort**: 2-3 hari  
**Action**: Track sent, delivered, read status

#### 12. Implement conversation archiving
**Effort**: 1-2 hari  
**Action**: Auto-archive conversation yang sudah selesai

#### 13. Add rate limiting untuk webhook
**Effort**: 1 hari  
**Action**: Prevent abuse dari WhatsApp webhook

---

## 🎯 Kesimpulan

### Modul yang Sudah Selesai ✅
- **Order Management**: Lengkap dan berfungsi baik
- **Product Management**: Lengkap dan berfungsi baik

### Modul yang Perlu Perbaikan ⚠️
- **Customer Service Chat Room**: 
  - Implementasi dasar sudah ada dan berfungsi
  - Perlu perbaikan error handling dan validasi
  - Arsitektur sudah bagus dengan WebSocket + WhatsApp integration
  
- **Chatbot Customer Service**:
  - **CRITICAL BUG**: `handleByBot` tidak diinisialisasi - chatbot tidak akan berfungsi sama sekali
  - Implementasi terlalu sederhana (hanya 3 rules)
  - Arsitektur event-driven sudah bagus
  - Perlu pengembangan besar untuk production-ready

### Overall Assessment
- **Code Quality**: 6/10
- **Architecture**: 7/10  
- **Completeness**: 6/10
- **Production Readiness**: 5/10

### Estimasi Effort untuk Production-Ready

| Kategori | Effort | Priority |
|----------|--------|----------|
| Fix critical bugs | 1-2 hari | 🔴 CRITICAL |
| Improve error handling & validation | 2-3 hari | 🟡 HIGH |
| Expand chatbot capabilities | 5-7 hari | 🟢 MEDIUM |
| **Total** | **~2 minggu** | - |

---

## 📞 Next Steps

1. **Immediate** (Hari ini):
   - Fix chatbot initialization bug
   - Move credentials ke environment variables

2. **This Week**:
   - Fix null pointer exceptions
   - Improve error handling
   - Add media validation

3. **Next Week**:
   - Expand chatbot rules
   - Add escalation logic
   - Implement analytics

4. **Future**:
   - OpenAI integration
   - Advanced features

---

**Review Date**: 2026-01-05  
**Reviewer**: AI Code Review Assistant  
**Project**: Saktiform API - Spring Boot Backend
