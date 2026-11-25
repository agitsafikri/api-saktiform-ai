package com.saktiform.api.service.order;

import com.saktiform.api.entity.Account;
import com.saktiform.api.entity.Order;
import com.saktiform.api.entity.Produk;
import com.saktiform.api.entity.Workspace;
import com.saktiform.api.model.Order.BulkUpdateStatus;
import com.saktiform.api.model.Order.OrderStatus;
import com.saktiform.api.model.account.Role;
import com.saktiform.api.repository.*;
import com.saktiform.api.service.formconfig.OrderCustomFieldService;
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
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

/**
 * Integration test counter {@code Produk.orderCount} / {@code Produk.soldCount}
 * terhadap PostgreSQL asli (Testcontainers).
 *
 * <p>Aturan: orderCount = jumlah order berstatus bukan DELETE,
 * soldCount = jumlah order berstatus PAID.
 *
 * <p>Perubahan status diuji lewat {@link OrderService#updateOrderStatus} (jalur bulk update
 * yang sebenarnya). Pergantian produk dan pembuatan order diuji lewat
 * {@link OrderService#applyProdukCounterChange}, helper yang dipakai {@code updateOrder},
 * {@code createOrderInternal} dan {@code createOrderOnChat}.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class ProdukCounterIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final String ACTOR = "owner-test";

    @Autowired private TestEntityManager em;
    @Autowired private OrderRepository orderRepository;
    @Autowired private ProdukRepository produkRepository;
    @Autowired private AccountRepository accountRepository;
    @Autowired private OrderHistoryRepository orderHistoryRepository;

    private OrderService orderService;
    private UUID produkA;
    private UUID produkB;

    @BeforeEach
    void setUp() {
        orderService = new OrderService(
                orderRepository,
                mock(OngkirRepository.class),
                mock(GudangRepository.class),
                produkRepository,
                mock(ProdukPembayaranRepository.class),
                mock(AtributProdukRepository.class),
                mock(AbandonedOrderRepository.class),
                mock(ContactRepository.class),
                mock(ProvinceRepository.class),
                mock(CityRepository.class),
                mock(DistrictRepository.class),
                accountRepository,
                mock(OrderSequenceRepository.class),
                orderHistoryRepository,
                mock(OrderCustomFieldService.class));

        Account owner = new Account();
        owner.setUsername(ACTOR);
        owner.setRole(Role.OWNER);
        owner.setCreatedAt(Instant.now());
        em.persist(owner);

        Workspace workspace = new Workspace();
        workspace.setNamaWorkspace("Workspace Test");
        workspace.setCreatedAt(Instant.now());
        Long workspaceId = em.persist(workspace).getId();

        produkA = persistProduk(workspaceId, 0L, 0L);
        produkB = persistProduk(workspaceId, 0L, 0L);

        em.flush();
        em.clear();
    }

    // ── perubahan status (updateOrderStatus) ──

    @Test
    @DisplayName("PAID -> DELETE: orderCount -1, soldCount -1")
    void paidToDelete() {
        UUID order = createOrder(produkA, OrderStatus.PAID);
        assertCounters(produkA, 1, 1);

        updateStatus(order, OrderStatus.DELETE);
        assertCounters(produkA, 0, 0);
    }

    @Test
    @DisplayName("UNPAID -> DELETE: orderCount -1, soldCount tetap")
    void unpaidToDelete() {
        UUID order = createOrder(produkA, OrderStatus.UNPAID);
        assertCounters(produkA, 1, 0);

        updateStatus(order, OrderStatus.DELETE);
        assertCounters(produkA, 0, 0);
    }

    @Test
    @DisplayName("DELETE -> PAID: orderCount +1, soldCount +1")
    void deleteToPaid() {
        UUID order = createOrder(produkA, OrderStatus.DELETE);
        assertCounters(produkA, 0, 0);

        updateStatus(order, OrderStatus.PAID);
        assertCounters(produkA, 1, 1);
    }

    @Test
    @DisplayName("DELETE -> UNPAID: orderCount +1, soldCount tetap")
    void deleteToUnpaid() {
        UUID order = createOrder(produkA, OrderStatus.DELETE);

        updateStatus(order, OrderStatus.UNPAID);
        assertCounters(produkA, 1, 0);
    }

    @Test
    @DisplayName("UNPAID -> PAID -> CANCELLED: orderCount tetap, soldCount naik lalu turun")
    void paidTransitionsWithoutDelete() {
        UUID order = createOrder(produkA, OrderStatus.UNPAID);

        updateStatus(order, OrderStatus.PAID);
        assertCounters(produkA, 1, 1);

        updateStatus(order, OrderStatus.CANCELLED);
        assertCounters(produkA, 1, 0);
    }

    @Test
    @DisplayName("Status tidak berubah: counter tidak berubah")
    void sameStatus() {
        UUID order = createOrder(produkA, OrderStatus.PAID);

        updateStatus(order, OrderStatus.PAID);
        assertCounters(produkA, 1, 1);
    }

    // ── pergantian produk & pembuatan order (helper) ──

    @Test
    @DisplayName("Ganti produk, tetap PAID: kedua counter pindah dari produk lama ke baru")
    void changeProdukStayPaid() {
        createOrder(produkA, OrderStatus.PAID);

        orderService.applyProdukCounterChange(produkA, "PAID", produkB, "PAID");
        assertCounters(produkA, 0, 0);
        assertCounters(produkB, 1, 1);
    }

    @Test
    @DisplayName("Ganti produk sekaligus PAID -> UNPAID: soldCount dicabut dari produk lama")
    void changeProdukPaidToUnpaid() {
        createOrder(produkA, OrderStatus.PAID);

        orderService.applyProdukCounterChange(produkA, "PAID", produkB, "UNPAID");
        assertCounters(produkA, 0, 0);
        assertCounters(produkB, 1, 0);
    }

    @Test
    @DisplayName("Ganti produk sekaligus ke DELETE: produk baru tidak bertambah")
    void changeProdukToDelete() {
        createOrder(produkA, OrderStatus.UNPAID);

        orderService.applyProdukCounterChange(produkA, "UNPAID", produkB, "DELETE");
        assertCounters(produkA, 0, 0);
        assertCounters(produkB, 0, 0);
    }

    @Test
    @DisplayName("Counter NULL pada produk lama diperlakukan sebagai 0")
    void nullCounters() {
        UUID legacy = persistProduk(em.find(Produk.class, produkA).getIdWorkspace(), null, null);
        em.flush();
        em.clear();

        orderService.applyProdukCounterChange(null, null, legacy, "PAID");
        assertCounters(legacy, 1, 1);
    }

    // ── helpers ──

    /** Meniru pembuatan order: simpan order lalu terapkan counter seperti createOrderInternal. */
    private UUID createOrder(UUID idProduk, OrderStatus status) {
        Order order = new Order();
        order.setIdProduk(idProduk);
        order.setStatus(status.name());
        order.setCreatedAt(Instant.now());
        if (status == OrderStatus.PAID) {
            order.setPaidAt(Instant.now());
        }
        UUID id = em.persistAndFlush(order).getId();
        orderService.applyProdukCounterChange(null, null, idProduk, status.name());
        em.clear();
        return id;
    }

    private void updateStatus(UUID orderId, OrderStatus status) {
        orderService.updateOrderStatus(List.of(new BulkUpdateStatus(orderId, status)), ACTOR);
        em.flush();
        em.clear();
    }

    private void assertCounters(UUID idProduk, long orderCount, long soldCount) {
        em.clear();
        Produk produk = em.find(Produk.class, idProduk);
        assertEquals(orderCount, produk.getOrderCount(), "orderCount");
        assertEquals(soldCount, produk.getSoldCount(), "soldCount");
    }

    private UUID persistProduk(Long idWorkspace, Long orderCount, Long soldCount) {
        Produk produk = new Produk();
        produk.setNamaProduk("Produk Test");
        produk.setUrlCheckout("produk-test-" + UUID.randomUUID());
        produk.setIdWorkspace(idWorkspace);
        produk.setOrderCount(orderCount);
        produk.setSoldCount(soldCount);
        return em.persist(produk).getId();
    }
}
