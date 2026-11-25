package com.saktiform.api.service;

import com.saktiform.api.entity.Order;
import com.saktiform.api.entity.Produk;
import com.saktiform.api.entity.Workspace;
import com.saktiform.api.model.Order.OrderStatus;
import com.saktiform.api.model.workspace.WorkspaceDashboardMatrix;
import com.saktiform.api.repository.AccountRepository;
import com.saktiform.api.repository.ChatTemplateRepository;
import com.saktiform.api.repository.GudangRepository;
import com.saktiform.api.repository.WorkspaceRepository;
import com.saktiform.api.service.chat.MessageConstructorHelper;
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
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

/**
 * Integration test {@link WorkspaceService#getDashboardMatrix} terhadap PostgreSQL asli
 * (Testcontainers), karena query ringkasan memakai sintaks khusus Postgres
 * ({@code COUNT(*) FILTER}, {@code IS DISTINCT FROM}).
 *
 * <p>Memastikan order berstatus DELETE tidak dihitung, dan bahwa perpindahan status
 * ke / dari DELETE langsung tercermin pada totalOrder, totalBayar, unpaidOrder
 * dan rasioBayar.
 *
 * <p>Data awal workspace: 2 PAID, 1 UNPAID, 1 PENDING, 1 CANCELLED, 1 DELETE
 * → totalOrder 5, totalBayar 2, unpaidOrder 3, rasioBayar 40.00%.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class WorkspaceDashboardMatrixIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private TestEntityManager em;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    private WorkspaceService workspaceService;

    private Long workspaceId;
    private UUID paidOrderId;
    private UUID unpaidOrderId;
    private UUID deletedOrderId;

    @BeforeEach
    void setUp() {
        workspaceService = new WorkspaceService(
                workspaceRepository,
                mock(GudangRepository.class),
                mock(AccountRepository.class),
                mock(MessageConstructorHelper.class),
                mock(ChatTemplateRepository.class),
                mock(DomainService.class));

        Workspace workspace = persistWorkspace("Workspace Test");
        workspaceId = workspace.getId();
        Produk produk = persistProduk(workspaceId);

        paidOrderId = persistOrder(produk, OrderStatus.PAID).getId();
        persistOrder(produk, OrderStatus.PAID);
        unpaidOrderId = persistOrder(produk, OrderStatus.UNPAID).getId();
        persistOrder(produk, OrderStatus.PENDING);
        persistOrder(produk, OrderStatus.CANCELLED);
        deletedOrderId = persistOrder(produk, OrderStatus.DELETE).getId();

        // Order workspace lain tidak boleh ikut terhitung.
        Workspace other = persistWorkspace("Workspace Lain");
        Produk otherProduk = persistProduk(other.getId());
        persistOrder(otherProduk, OrderStatus.PAID);
        persistOrder(otherProduk, OrderStatus.UNPAID);

        em.flush();
        em.clear();
    }

    @Test
    @DisplayName("Order DELETE tidak dihitung pada data awal")
    void excludesDeletedOrders() {
        assertMatrix(5, 2, 3, "40.00%");
    }

    @Test
    @DisplayName("PAID -> DELETE: totalOrder & totalBayar berkurang, rasio dihitung ulang")
    void paidToDelete() {
        changeStatus(paidOrderId, OrderStatus.DELETE);
        assertMatrix(4, 1, 3, "25.00%");
    }

    @Test
    @DisplayName("UNPAID -> DELETE: totalOrder & unpaidOrder berkurang, rasio dihitung ulang")
    void unpaidToDelete() {
        changeStatus(unpaidOrderId, OrderStatus.DELETE);
        assertMatrix(4, 2, 2, "50.00%");
    }

    @Test
    @DisplayName("DELETE -> PAID: totalOrder & totalBayar bertambah, rasio dihitung ulang")
    void deleteToPaid() {
        changeStatus(deletedOrderId, OrderStatus.PAID);
        assertMatrix(6, 3, 3, "50.00%");
    }

    @Test
    @DisplayName("DELETE -> UNPAID: totalOrder & unpaidOrder bertambah, rasio dihitung ulang")
    void deleteToUnpaid() {
        changeStatus(deletedOrderId, OrderStatus.UNPAID);
        assertMatrix(6, 2, 4, "33.33%");
    }

    @Test
    @DisplayName("Semua order DELETE: seluruh angka nol, rasio 0.00%")
    void allDeleted() {
        em.getEntityManager()
                .createQuery("UPDATE Order o SET o.status = :status")
                .setParameter("status", OrderStatus.DELETE.name())
                .executeUpdate();
        em.clear();

        assertMatrix(0, 0, 0, "0.00%");
    }

    // ── helpers ──

    /** Meniru perubahan status pada OrderService: paidAt diisi saat PAID, dikosongkan selain itu. */
    private void changeStatus(UUID orderId, OrderStatus status) {
        Order order = em.find(Order.class, orderId);
        order.setStatus(status.name());
        order.setPaidAt(status == OrderStatus.PAID ? Instant.now() : null);
        order.setUpdatedAt(Instant.now());
        em.flush();
        em.clear();
    }

    private void assertMatrix(int totalOrder, int totalBayar, int unpaidOrder, String rasioBayar) {
        WorkspaceDashboardMatrix matrix = workspaceService.getDashboardMatrix(workspaceId, null, null);
        assertEquals(totalOrder, matrix.getTotalOrder(), "totalOrder");
        assertEquals(totalBayar, matrix.getTotalBayar(), "totalBayar");
        assertEquals(unpaidOrder, matrix.getUnpaidOrder(), "unpaidOrder");
        assertEquals(rasioBayar, matrix.getRasioBayar(), "rasioBayar");
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
        order.setCreatedAt(Instant.now().minus(1, ChronoUnit.DAYS));
        if (status == OrderStatus.PAID) {
            order.setPaidAt(Instant.now());
        }
        return em.persist(order);
    }
}
