package com.saktiform.api.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * Staging: setiap baris hasil parsing Excel + klasifikasi (EXISTING/NEW/INVALID/DUPLICATE).
 */
@Getter
@Setter
@Entity
@Table(name = "blast_import_contact",
        indexes = {
                @Index(name = "idx_bic_import_category", columnList = "import_id, category"),
                @Index(name = "idx_bic_ws_phone", columnList = "id_workspace, normalized_phone")
        })
public class BlastImportContact {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "import_id", nullable = false)
    private Long importId;

    @Column(name = "id_workspace", nullable = false)
    private Long idWorkspace;

    @Column(name = "row_number")
    private Integer rowNumber;

    @Column(name = "raw_name", length = Integer.MAX_VALUE)
    private String rawName;

    @Column(name = "raw_phone", length = 64)
    private String rawPhone;

    @Column(name = "normalized_phone", length = 20)
    private String normalizedPhone;

    @Column(name = "category", length = 16)
    private String category;

    @Column(name = "invalid_reason", length = 128)
    private String invalidReason;

    @Column(name = "contact_id")
    private Long contactId;

    @Column(name = "created_at")
    private Instant createdAt;
}
