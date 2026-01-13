package com.saktiform.api.service.chat.bot;

import io.qdrant.client.QdrantClient;
import io.qdrant.client.QdrantGrpcClient;
import io.qdrant.client.grpc.Collections.Distance;
import io.qdrant.client.grpc.Collections.VectorParams;
import io.qdrant.client.grpc.Points;
import io.qdrant.client.grpc.JsonWithInt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

@Service
public class QdrantVectorService {
    private static final Logger log = LoggerFactory.getLogger(QdrantVectorService.class);

    private QdrantClient client;
    private final String host;
    private final int port;
    private final String apiKey;

    public static final String COLLECTION_KNOWLEDGE = "knowledge_base";
    public static final String COLLECTION_TENDENCIES = "user_tendencies";
    private static final int VECTOR_SIZE = 1536; // OpenAI text-embedding-3-small

    public QdrantVectorService(
            @Value("${qdrant.host:103.49.239.5}") String host,
            @Value("${qdrant.port:6334}") int port,
            @Value("${qdrant.api.key:}") String apiKey) {
        this.host = host;
        this.port = port;
        this.apiKey = apiKey;
    }

    @PostConstruct
    public void init() {
        try {
            QdrantGrpcClient.Builder grpcBuilder = QdrantGrpcClient.newBuilder(host, port, false);
            if (apiKey != null && !apiKey.isBlank()) {
                grpcBuilder.withApiKey(apiKey);
            }
            this.client = new QdrantClient(grpcBuilder.build());

            // Note: In production, collection creation should typically be done via
            // migration scripts,
            // but for this MVP we can check/create here if needed, or assume they exist.
            ensureCollection(COLLECTION_KNOWLEDGE);
            ensureCollection(COLLECTION_TENDENCIES);

        } catch (Exception e) {
            log.error("Failed to initialize Qdrant client: {}", e.getMessage());
        }
    }

    private void ensureCollection(String collectionName) {
        try {
            boolean exists = client.listCollectionsAsync().get().stream()
                    .anyMatch(c -> c.equals(collectionName));

            if (!exists) {
                log.info("Creating collection: {}", collectionName);
                client.createCollectionAsync(
                        collectionName,
                        VectorParams.newBuilder()
                                .setSize(VECTOR_SIZE)
                                .setDistance(Distance.Cosine)
                                .build())
                        .get();
            }
        } catch (Exception e) {
            log.warn("Could not ensure collection {}: {}", collectionName, e.getMessage());
        }
    }

    public List<String> searchKnowledge(List<Float> embedding) {
        try {
            // Basic search implementation
            List<Points.ScoredPoint> points = client.searchAsync(
                    Points.SearchPoints.newBuilder()
                            .setCollectionName(COLLECTION_KNOWLEDGE)
                            .addAllVector(embedding)
                            .setLimit(3)
                            .setWithPayload(Points.WithPayloadSelector.newBuilder().setEnable(true).build())
                            .build())
                    .get();

            return points.stream()
                    .map(p -> {
                        JsonWithInt.Value value = p.getPayloadMap()
                                .getOrDefault("content", JsonWithInt.Value.newBuilder().setStringValue("").build());
                        return value.getStringValue();
                    })
                    .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("Knowledge search failed: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    // Placeholder for User Tendencies search (Requires filtering by UserID)
    public List<String> searchTendencies(List<Float> embedding, UUID userId) {
        try {
            // Implementation for filtered search would go here using Qdrant Filter
            // For now returning empty to ensure code compilation if complex Filter Logic is
            // missing
            return Collections.emptyList();
        } catch (Exception e) {
            log.error("Tendency search failed: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    public void close() {
        if (client != null) {
            client.close();
        }
    }
}
