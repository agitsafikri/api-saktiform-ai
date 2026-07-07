package com.saktiform.api.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * Header satu sesi upload Excel + ringkasan hasil analisis kontak.
 * 1 import = 1 campaign (BR-22): status berakhir CONSUMED setelah campaign dibuat.
 */
@Getter
@Setter
@Entity
@Table(name = "blast_import",
        indexes = @Index(name = "idx_blast_import_ws", columnList = "id_workspace, created_at"))
public class BlastImport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "id_workspace", nullable = false)
    private Long idWorkspace;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "file_name", length = Integer.MAX_VALUE)
    private String fileName;

    @Column(name = "file_path", length = Integer.MAX_VALUE)
    private String filePath;

    @Column(name = "status", length = 32)
    private String status;

    @Column(name = "total_upload")
    private Integer totalUpload = 0;

    @Column(name = "total_valid")
    private Integer totalValid = 0;

    @Column(name = "total_invalid")
    private Integer totalInvalid = 0;

    @Column(name = "total_duplicate")
    private Integer totalDuplicate = 0;

    @Column(name = "total_existing")
    private Integer totalExisting = 0;

    @Column(name = "total_new")
    private Integer totalNew = 0;

    @Version
    @Column(name = "version")
    private Long version;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;
}
