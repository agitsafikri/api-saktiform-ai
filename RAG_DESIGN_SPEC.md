# RAG Chatbot Architecture with Context Tendencies

## 1. Architecture Overview

The system enhances a standard RAG loop by injecting "Context Tendencies"—persistent behavioral patterns of the user—into the generation context.

### Core Components
*   **API Layer (Spring Boot)**: Handles websocket/REST chat requests.
*   **Orchestrator**: Coordinates retrieval, context building, and LLM execution.
*   **Vector Database (Qdrant)**: Stores both static knowledge and dynamic user tendencies.
*   **LLM (OpenAI)**: Generates responses and analyzes interactions to update tendencies.
*   **Async Worker**: Handles background tendency updates to minimize chat latency.

---

## 2. Qdrant Schema

We require two distinct collections.

### Collection A: `knowledge_base`
Stores static or semi-static content (documents, FAQs, product info).

*   **Vector Config**: 1536 dimensions (text-embedding-3-small), Cosine distance.
*   **Payload Structure**:
    ```json
    {
      "source_id": "doc_123",
      "content": "Product X costs $50...",
      "type": "product_info", // or 'faq', 'sop'
      "tags": ["pricing", "product_x"],
      "created_at": "timestamp"
    }
    ```
*   **Indexes**: Index `type` and `tags` for filtering.

### Collection B: `user_tendencies`
Stores dynamic behavioral vectors per user.

*   **Vector Config**: 1536 dimensions, Cosine distance.
*   **Payload Structure**:
    ```json
    {
      "user_id": "uuid_user_1",
      "tendency_type": "price_sensitivity", // e.g., 'intent', 'tone', 'category_interest'
      "description": "User frequently asks about discounts and low prices",
      "strength": 0.85, // Confidence/Frequency score
      "last_triggered": "timestamp",
      "occurrence_count": 5
    }
    ```
*   **Indexes**: **CRITICAL** - Filterable index on `user_id`.

---

## 3. Chat Flow Architecture

### Sequence
1.  **User Message**: User sends "Do you have any cheaper options for laptops?"
2.  **Embedding**: Generate vector `v_msg` for the user message.
3.  **Parallel Retrieval**:
    *   **Knowledge Search**: Search `knowledge_base` using `v_msg` (Top 3).
    *   **Tendency Search**: Search `user_tendencies` using `v_msg` **AND** Filter `user_id == current_user` (Top 3).
        *   *Goal*: Find past behaviors relevant to the *current* query (e.g., retrieving a "prefers budget items" tendency).
4.  **Context Assembly**:
    *   Combine `Knowledge Chunks` + `Relevant Tendencies`.
    *   *Tendency Context Example*: "User Tendency: User prefers budget-friendly options and often rejects items > $500."
5.  **LLM Generation**: Generate response using enhanced context.
6.  **Response Delivery**: Send reply to user.
7.  **Async Tendency Update**:
    *   Analyze (User Message + bot Reply).
    *   Did this interaction reinforce an existing tendency or create a new one?
    *   Upsert to `user_tendencies`.

---

## 4. Prompt Engineering

### System Prompt
```text
You are an expert sales assistant.
Your goal is to answer user questions using the provided KNOWLEDGE BASE.
You must also adapt your tone and recommendations based on the USER TENDENCIES provided.

RULES:
1. Prioritize USER TENDENCIES. If the user has a tendency to dislike expensive items, do not recommend them unless asked.
2. Use the KNOWLEDGE BASE for facts. If the answer is not there, say "I don't have that info".
3. Do not explicitly mention "I see from your tendencies...". Just act on them naturally.
```

### Dynamic Context Prompt
```text
[USER TENDENCIES]
- User prefers formal tone.
- User is interested in gaming laptops.
- User is price-sensitive (Budget < $10M IDR).

[KNOWLEDGE BASE]
- Laptop A: $15M IDR, High specs.
- Laptop B: $8M IDR, Mid specs.

[USER QUERY]
"Show me good computers."
```

---

## 5. Backend Implementation (Java/Spring Boot)

### Pseudocode Components

#### `TendencyService`
```java
public void updateTendencies(String userId, String userMessage, String botReply) {
    // 1. Analyze interaction using LLM
    TendencyAnalysis analysis = llmClient.analyze(userMessage, botReply);
    
    if (analysis.isNewTendency() || analysis.isReinforcement()) {
        // 2. Create embedding for the tendency description
        float[] vector = embeddingClient.embed(analysis.getDescription());
        
        // 3. Upsert to Qdrant
        PointStruct point = PointStruct.builder()
            .id(pointId(userId, analysis.getType())) // Deterministic ID to overwrite/update
            .vector(vector)
            .putPayload("user_id", userId)
            .putPayload("description", analysis.getDescription())
            .putPayload("occurrence_count", analysis.getNewCount())
            .build();
            
        qdrantClient.upsertAsync("user_tendencies", List.of(point));
    }
}
```

#### `ChatOrchestrator`
```java
public String handleChat(String userId, String message) {
    // 1. Embed Message
    float[] msgVector = embeddingClient.embed(message);
    
    // 2. Retrieve Context (Parallel)
    CompletableFuture<List<ScoredPoint>> knowledgeFuture = 
        qdrantClient.search("knowledge_base", msgVector, 3);
        
    CompletableFuture<List<ScoredPoint>> tendencyFuture = 
        qdrantClient.search("user_tendencies", msgVector, 
            Filter.must(match("user_id", userId)), 3);
            
    // 3. Construct Prompt
    String context = buildContext(knowledgeFuture.join(), tendencyFuture.join());
    
    // 4. Generate
    String reply = llmClient.generate(systemPrompt, context, message);
    
    // 5. Async Update
    asyncTaskExecutor.submit(() -> tendencyService.updateTendencies(userId, message, reply));
    
    return reply;
}
```

---

## 6. Scalability & Safety

### Scaling
*   **Qdrant Partitioning**: Shard via Qdrant cluster if users > 1M.
*   **UserID Filter**: Essential for `user_tendencies`. Ensure `user_id` is a payload index in Qdrant for O(1) filtering performance before vector search.

### Context Management
*   **Decay**: In `TendencyService`, check timestamps. If a tendency hasn't been triggered in 30 days, lower its `strength` or delete it.
*   **Limit**: Retrieve max 3-5 tendencies per chat to avoid polluting context window.

### Safety
*   **Data Isolation**: Strict `user_id` filtering in Qdrant is mandatory. Never search `user_tendencies` without the filter.
