package com.saktiform.api.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "order_contact")
public class OrderContact {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_conversation", insertable = false, updatable = false)
    private Conversation conversation;

    @Column(name = "id_conversation")
    private UUID idConversation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_order", insertable = false, updatable = false)
    private Order order;

    @Column(name = "id_order")
    private UUID idOrder;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

}