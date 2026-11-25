package com.saktiform.api.repository;

import com.saktiform.api.entity.Produk;
import com.saktiform.api.entity.Workspace;
import com.saktiform.api.model.product.ProdukListDropdown;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Integration test query dropdown produk ({@code GET /produk/list-dropdown}) terhadap
 * PostgreSQL asli: filter nama, isolasi workspace, produk terhapus, dan paginasi.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class ProdukDropdownRepositoryIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final Sort SORT = Sort.by("namaProduk", "id");

    @Autowired private TestEntityManager em;
    @Autowired private ProdukRepository produkRepository;

    private Long workspaceId;

    @BeforeEach
    void setUp() {
        workspaceId = workspace("Workspace Test");
        produk(workspaceId, "Kopi Arabika", false);
        produk(workspaceId, "Kopi Robusta", false);
        produk(workspaceId, "Teh Hijau", false);
        produk(workspaceId, "Susu Kopi", false);
        produk(workspaceId, "Kopi Lama", true); // terhapus: tidak boleh muncul

        produk(workspace("Workspace Lain"), "Kopi Tetangga", false);

        em.flush();
        em.clear();
    }

    @Test
    @DisplayName("Tanpa search: semua produk aktif workspace, urut nama")
    void noSearch() {
        assertEquals(List.of("Kopi Arabika", "Kopi Robusta", "Susu Kopi", "Teh Hijau"),
                names(produkRepository.findAllProdukListDropdown(workspaceId, "", SORT)));
    }

    @Test
    @DisplayName("Search: mengandung, tanpa peka huruf; tanpa produk terhapus/workspace lain")
    void searchByName() {
        assertEquals(List.of("Kopi Arabika", "Kopi Robusta", "Susu Kopi"),
                names(produkRepository.findAllProdukListDropdown(workspaceId, "kOPi", SORT)));
        assertEquals(List.of(), names(produkRepository.findAllProdukListDropdown(workspaceId, "tidak ada", SORT)));
    }

    @Test
    @DisplayName("Paginasi: total & isi halaman mengikuti filter")
    void pagination() {
        Page<ProdukListDropdown> first = produkRepository.findPageProdukListDropdown(
                workspaceId, "kopi", PageRequest.of(0, 2, SORT));
        Page<ProdukListDropdown> second = produkRepository.findPageProdukListDropdown(
                workspaceId, "kopi", PageRequest.of(1, 2, SORT));

        assertEquals(3, first.getTotalElements());
        assertEquals(2, first.getTotalPages());
        assertEquals(List.of("Kopi Arabika", "Kopi Robusta"), names(first.getContent()));
        assertEquals(List.of("Susu Kopi"), names(second.getContent()));
    }

    // ── helpers ──

    private static List<String> names(List<ProdukListDropdown> rows) {
        return rows.stream().map(ProdukListDropdown::getNamaProduk).toList();
    }

    private Long workspace(String nama) {
        Workspace workspace = new Workspace();
        workspace.setNamaWorkspace(nama);
        workspace.setCreatedAt(Instant.now());
        return em.persist(workspace).getId();
    }

    private void produk(Long idWorkspace, String nama, boolean deleted) {
        Produk produk = new Produk();
        produk.setNamaProduk(nama);
        produk.setUrlCheckout("produk-test-" + UUID.randomUUID());
        produk.setIdWorkspace(idWorkspace);
        produk.setIsDeleted(deleted);
        em.persist(produk);
    }
}
