package com.saktiform.api.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * Master label per-workspace: teks (name) + kode warna hex (#rrggbb).
 * Reusable — satu label dapat dipakai di banyak conversation via {@link ConversationLabelLink}.
 *
 * <p>Unique case-insensitive {@code (id_workspace, lower(name))} dibuat oleh
 * {@code LabelSchemaInitializer} pada startup (functional index tidak dapat dihasilkan Hibernate).
 */
@Getter
@Setter
@Entity
@Table(name = "conversation_label",
        indexes = @Index(name = "idx_label_ws", columnList = "id_workspace, name"))
public class ConversationLabel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "id_workspace", nullable = false)
    private Long idWorkspace;

    @Column(name = "name", length = Integer.MAX_VALUE, nullable = false)
    private String name;

    @Column(name = "color_hex", length = 7, nullable = false)
    private String colorHex;            // tersimpan ternormalisasi: #rrggbb

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;
}
