package com.saktiform.api.repository;

import com.saktiform.api.entity.*;
import com.saktiform.api.model.Order.OrderStatus;
import com.saktiform.api.model.product.ProdukOrderStatistikView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Integration test {@link ProdukRepository#getOrderStatistikByProduk} terhadap PostgreSQL asli.
 *
 * <p>Produk A, order masuk (created_at) → status, paid_at:
 * <pre>
 * 31 Okt  PAID     (dibayar 2 Nov)   ← di luar rentang November
 *  5 Nov  PAID     (dibayar 5 Nov)
 * 10 Nov  UNPAID
 * 15 Nov  DELETE                     ← tidak dihitung
 * 20 Nov  NULL                       ← dihitung sebagai order
 * 29 Nov  PAID     (dibayar 3 Des)   ← tetap dihitung dibayar
 *  1 Des  PAID
 * </pre>
 * Produk B: tidak punya order. Produk C: satu order PAID 12 Nov.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class ProdukOrderStatistikIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final LocalDateTime AWAL_NOV = LocalDateTime.of(2026, 11, 1, 0, 0, 0);
    private static final LocalDateTime AKHIR_NOV = LocalDateTime.of(2026, 11, 30, 23, 59, 59);

    @Autowired private TestEntityManager em;
    @Autowired private ProdukRepository produkRepository;

    private UUID produkA, produkB, produkC;

    @BeforeEach
    void setUp() {
        Long workspaceId = workspace();
        Integer prov = province();
        Integer kota = city(prov);
        Integer kec = district(kota);

        produkA = produk(workspaceId);
        produkB = produk(workspaceId);
        produkC = produk(workspaceId);

        order(produkA, "A1", at(2026, 10, 31), "PAID", at(2026, 11, 2), prov, kota, kec);
        order(produkA, "A2", at(2026, 11, 5), "PAID", at(2026, 11, 5), prov, kota, kec);
        order(produkA, "A3", at(2026, 11, 10), "UNPAID", null, prov, kota, kec);
        order(produkA, "A4", at(2026, 11, 15), OrderStatus.DELETE.name(), null, prov, kota, kec);
        order(produkA, "A5", at(2026, 11, 20), null, null, prov, kota, kec);
        order(produkA, "A6", at(2026, 11, 29), "PAID", at(2026, 12, 3), prov, kota, kec);
        order(produkA, "A7", at(2026, 12, 1), "PAID", at(2026, 12, 1), prov, kota, kec);
        order(produkC, "C1", at(2026, 11, 12), "PAID", at(2026, 11, 12), prov, kota, kec);

        em.flush();
        em.clear();
    }

    @Test
    @DisplayName("Rentang November: hanya created_at yang menentukan; paid_at diabaikan")
    void november() {
        Map<UUID, long[]> result = stats(List.of(produkA, produkB, produkC), AWAL_NOV, AKHIR_NOV);

        // A2, A3, A5, A6 (A4 DELETE tidak dihitung); dibayar A2, A6.
        assertEquals(4L, result.get(produkA)[0]);
        assertEquals(2L, result.get(produkA)[1]);
        assertEquals(1L, result.get(produkC)[0]);
        assertEquals(1L, result.get(produkC)[1]);
        // Produk tanpa order tidak muncul (pemanggil menganggapnya 0).
        assertEquals(false, result.containsKey(produkB));
    }

    @Test
    @DisplayName("Rentang terbuka: hanya tanggal awal / hanya tanggal akhir")
    void openRange() {
        // >= 1 Nov: A2, A3, A5, A6, A7 → 5 order, dibayar A2, A6, A7.
        long[] sejakNov = stats(List.of(produkA), AWAL_NOV, null).get(produkA);
        assertEquals(5L, sejakNov[0]);
        assertEquals(3L, sejakNov[1]);

        // <= 30 Nov: A1, A2, A3, A5, A6 → 5 order, dibayar A1, A2, A6.
        long[] sampaiNov = stats(List.of(produkA), null, AKHIR_NOV).get(produkA);
        assertEquals(5L, sampaiNov[0]);
        assertEquals(3L, sampaiNov[1]);
    }

    @Test
    @DisplayName("Hanya produk yang diminta yang dihitung")
    void onlyRequestedProducts() {
        Map<UUID, long[]> result = stats(List.of(produkC), AWAL_NOV, AKHIR_NOV);
        assertEquals(1, result.size());
        assertEquals(1L, result.get(produkC)[0]);
    }

    // ── helpers ──

    private Map<UUID, long[]> stats(List<UUID> ids, LocalDateTime awal, LocalDateTime akhir) {
        return produkRepository.getOrderStatistikByProduk(ids, awal, akhir).stream()
                .collect(Collectors.toMap(ProdukOrderStatistikView::getIdProduk,
                        v -> new long[]{v.getTotalOrder(), v.getTotalDibayar()}));
    }

    /** Tengah hari UTC agar tidak bergeser hari karena zona waktu sesi database. */
    private static Instant at(int year, int month, int day) {
        return LocalDateTime.of(year, month, day, 12, 0).toInstant(ZoneOffset.UTC);
    }

    private Long workspace() {
        Workspace workspace = new Workspace();
        workspace.setNamaWorkspace("Workspace Test");
        workspace.setCreatedAt(Instant.now());
        return em.persist(workspace).getId();
    }

    private UUID produk(Long idWorkspace) {
        Produk produk = new Produk();
        produk.setNamaProduk("Produk " + UUID.randomUUID());
        produk.setUrlCheckout("produk-test-" + UUID.randomUUID());
        produk.setIdWorkspace(idWorkspace);
        produk.setIsDeleted(false);
        return em.persist(produk).getId();
    }

    private Integer province() {
        Province province = new Province();
        province.setProvinceName("Jawa Barat");
        return em.persist(province).getId();
    }

    private Integer city(Integer idProvince) {
        City city = new City();
        city.setCityName("Bandung");
        city.setIdProvince(idProvince);
        return em.persist(city).getId();
    }

    private Integer district(Integer idCity) {
        District district = new District();
        district.setDistrictName("Coblong");
        district.setIdCity(idCity);
        return em.persist(district).getId();
    }

    private void order(UUID idProduk, String code, Instant createdAt, String status, Instant paidAt,
                       Integer prov, Integer kota, Integer kec) {
        Order order = new Order();
        order.setIdProduk(idProduk);
        order.setOrderCode(code);
        order.setNamaPenerima("Pelanggan " + code);
        order.setNomorWhatsapp("62812" + code);
        order.setIdProvinsi(prov);
        order.setIdKota(kota);
        order.setIdKecamatan(kec);
        order.setStatus(status);
        order.setPembayaran("COD");
        order.setHarga(100_000L);
        order.setOngkosKirim(10_000L);
        order.setBerat(500);
        order.setCreatedAt(createdAt);
        order.setPaidAt(paidAt);
        em.persist(order);
    }
}
