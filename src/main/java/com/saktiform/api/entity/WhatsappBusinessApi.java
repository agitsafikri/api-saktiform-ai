package com.saktiform.api.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "whatsapp_business_api")
public class WhatsappBusinessApi {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "nomor_whatsapp", length = Integer.MAX_VALUE)
    private String nomorWhatsapp;


    @Column(name = "api_key", nullable = false, length = Integer.MAX_VALUE)
    private String apiKey;


    @Column(name = "api_id", nullable = false, length = Integer.MAX_VALUE)
    private String apiId;


    @Column(name = "status", nullable = false, length = Integer.MAX_VALUE)
    private String status;

    @Column(name = "port", nullable = false, length = Integer.MAX_VALUE)
    private Integer port;


    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

}