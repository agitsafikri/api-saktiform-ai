package com.saktiform.api.repository;

import com.saktiform.api.entity.Order;
import com.saktiform.api.entity.Produk;
import com.saktiform.api.entity.Workspace;
import com.saktiform.api.model.Order.OrderStatus;
import com.saktiform.api.model.Order.TotalOrderReportView;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Integration test query laporan {@code /workspace/{id}/dashboard-order}
 * (harian, mingguan, bulanan) terhadap PostgreSQL asli (Testcontainers).
 *
 * <p>Memastikan order berstatus DELETE tidak dihitung pada jumlahOrder, dan perpindahan
 * status ke / dari DELETE langsung tercermin pada jumlahOrder dan jumlahBayar.
 *
 * <p>Data awal (semua dibuat & dibayar pada 10 Nov 2026): 2 PAID, 1 UNPAID, 1 DELETE
 * → jumlahOrder 3, jumlahBayar 2.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class DashboardOrderReportIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    /** Tengah hari (WIB) agar tanggal tidak bergeser karena zona waktu sesi. */
    private static final Instant ORDER_TIME = Instant.parse("2026-11-10T05:00:00Z");
    private static final LocalDateTime START = LocalDateTime.of(2026, 11, 1, 0, 0);
    private static final LocalDateTime END = LocalDateTime.of(2026, 11, 30, 23, 59, 59);

    @Autowired
    private TestEntityManager em;

    @Autowired
    private OrderRepository orderRepository;

    private Long workspaceId;
    private UUID paidOrderId;
    private UUID unpaidOrderId;
    private UUID deletedOrderId;

    @BeforeEach
    void setUp() {
        workspaceId = persistWorkspace("Workspace Test").getId();
        Produk produk = persistProduk(workspaceId);

        paidOrderId = persistOrder(produk, OrderStatus.PAID).getId();
        persistOrder(produk, OrderStatus.PAID);
        unpaidOrderId = persistOrder(produk, OrderStatus.UNPAID).getId();
        deletedOrderId = persistOrder(produk, OrderStatus.DELETE).getId();

        // Order workspace lain tidak boleh ikut terhitung.
        Produk otherProduk = persistProduk(persistWorkspace("Workspace Lain").getId());
        persistOrder(otherProduk, OrderStatus.PAID);
        persistOrder(otherProduk, OrderStatus.UNPAID);

        em.flush();
        em.clear();
    }

    @Test
    @DisplayName("Order DELETE tidak dihitung pada data awal")
    void excludesDeletedOrders() {
        assertReport(3, 2);
    }

    @Test
    @DisplayName("PAID -> DELETE: jumlahOrder & jumlahBayar berkurang")
    void paidToDelete() {
        changeStatus(paidOrderId, OrderStatus.DELETE);
        assertReport(2, 1);
    }

    @Test
    @DisplayName("UNPAID -> DELETE: jumlahOrder berkurang, jumlahBayar tetap")
    void unpaidToDelete() {
        changeStatus(unpaidOrderId, OrderStatus.DELETE);
        assertReport(2, 2);
    }

    @Test
    @DisplayName("DELETE -> PAID: jumlahOrder & jumlahBayar bertambah")
    void deleteToPaid() {
        changeStatus(deletedOrderId, OrderStatus.PAID);
        assertReport(4, 3);
    }

    @Test
    @DisplayName("DELETE -> UNPAID: jumlahOrder bertambah, jumlahBayar tetap")
    void deleteToUnpaid() {
        changeStatus(deletedOrderId, OrderStatus.UNPAID);
        assertReport(4, 2);
    }

    // ── helpers ──

    /** Memeriksa total jumlahOrder & jumlahBayar pada ketiga granularitas laporan. */
    private void assertReport(long expectedOrder, long expectedBayar) {
        Map<String, List<TotalOrderReportView>> reports = Map.of(
                "harian", orderRepository.getDailyReportTotalOrder(START, END, workspaceId),
                "mingguan", orderRepository.getWeeklyReportTotalOrder(START, END, workspaceId),
                "bulanan", orderRepository.getMonthlyReportTotalOrder(START, END, workspaceId));

        reports.forEach((granularity, rows) -> {
            long jumlahOrder = rows.stream().mapToLong(TotalOrderReportView::getJumlahOrder).sum();
            long jumlahBayar = rows.stream().mapToLong(TotalOrderReportView::getJumlahBayar).sum();
            assertEquals(expectedOrder, jumlahOrder, "jumlahOrder " + granularity);
            assertEquals(expectedBayar, jumlahBayar, "jumlahBayar " + granularity);
        });
    }

    /** Meniru perubahan status pada OrderService: paidAt diisi saat PAID, dikosongkan selain itu. */
    private void changeStatus(UUID orderId, OrderStatus status) {
        Order order = em.find(Order.class, orderId);
        order.setStatus(status.name());
        order.setPaidAt(status == OrderStatus.PAID ? ORDER_TIME : null);
        order.setUpdatedAt(Instant.now());
        em.flush();
        em.clear();
    }

    private Workspace persistWorkspace(String nama) {
        Workspace workspace = new Workspace();
        workspace.setNamaWorkspace(nama);
        workspace.setCreatedAt(Instant.now());
        return em.persist(workspace);
    }

    private Produk persistProduk(Long idWorkspace) {
        Produk produk = new Produk();
        produk.setNamaProduk("Produk Test");
        produk.setUrlCheckout("produk-test-" + UUID.randomUUID());
        produk.setIdWorkspace(idWorkspace);
        return em.persist(produk);
    }

    private Order persistOrder(Produk produk, OrderStatus status) {
        Order order = new Order();
        order.setIdProduk(produk.getId());
        order.setStatus(status.name());
        order.setCreatedAt(ORDER_TIME);
        if (status == OrderStatus.PAID) {
            order.setPaidAt(ORDER_TIME);
        }
        return em.persist(order);
    }
}
