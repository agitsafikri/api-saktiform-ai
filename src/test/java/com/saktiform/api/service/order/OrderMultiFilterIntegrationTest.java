package com.saktiform.api.service.order;

import com.saktiform.api.entity.*;
import com.saktiform.api.model.Order.ExportOrderListDto;
import com.saktiform.api.model.Order.OrderListDto;
import com.saktiform.api.model.Order.OrderListFilter;
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
import org.springframework.data.domain.Page;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

/**
 * Integration test filter multi-nilai {@code GET /order} dan {@code GET /order/export}
 * terhadap PostgreSQL asli (Testcontainers). Lihat docs/prd/order-multi-filter.md §10.
 *
 * <p>Data workspace (kode order → produk, provinsi/kota/kecamatan, status, pembayaran):
 * <pre>
 * O1  A  P1 / C1a / D1a1   PAID       COD
 * O2  A  P1 / C1a / D1a2   UNPAID     BANK_TRANSFER
 * O3  A  P1 / C1b / D1b    PENDING    COD
 * O4  B  P2 / C2  / D2     UNPAID     COD
 * O5  B  P3 / C3  / D3     CANCELLED  BANK_TRANSFER
 * O6  B  P2 / C2  / D2     DELETE     COD
 * </pre>
 * Ditambah satu order di workspace lain yang tidak boleh pernah muncul.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class OrderMultiFilterIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final String OWNER = "owner-test";
    private static final String ADMIN = "admin-test";

    @Autowired private TestEntityManager em;
    @Autowired private OrderRepository orderRepository;
    @Autowired private AccountRepository accountRepository;

    private OrderService orderService;
    private Long workspaceId;

    private Integer p1, p2, p3, c1a, c1b, c2, c3, d1a1, d1a2, d1b, d2, d3;
    private UUID produkA, produkB, otherProduk;

    @BeforeEach
    void setUp() {
        orderService = new OrderService(orderRepository, mock(OngkirRepository.class), mock(GudangRepository.class),
                mock(ProdukRepository.class), mock(ProdukPembayaranRepository.class),
                mock(AtributProdukRepository.class), mock(AbandonedOrderRepository.class),
                mock(ContactRepository.class), mock(ProvinceRepository.class), mock(CityRepository.class),
                mock(DistrictRepository.class), accountRepository, mock(OrderSequenceRepository.class),
                mock(OrderHistoryRepository.class), mock(OrderCustomFieldService.class));

        persistAccount(OWNER, Role.OWNER);
        persistAccount(ADMIN, Role.ADMIN);

        p1 = province("Jawa Barat");
        p2 = province("Jawa Tengah");
        p3 = province("Jawa Timur");
        c1a = city("Bandung", p1);
        c1b = city("Bogor", p1);
        c2 = city("Semarang", p2);
        c3 = city("Surabaya", p3);
        d1a1 = district("Coblong", c1a);
        d1a2 = district("Sukajadi", c1a);
        d1b = district("Bogor Tengah", c1b);
        d2 = district("Tembalang", c2);
        d3 = district("Gubeng", c3);

        workspaceId = workspace("Workspace Test");
        produkA = produk(workspaceId);
        produkB = produk(workspaceId);
        order(produkA, "O1", p1, c1a, d1a1, OrderStatus.PAID, "COD");
        order(produkA, "O2", p1, c1a, d1a2, OrderStatus.UNPAID, "BANK_TRANSFER");
        order(produkA, "O3", p1, c1b, d1b, OrderStatus.PENDING, "COD");
        order(produkB, "O4", p2, c2, d2, OrderStatus.UNPAID, "COD");
        order(produkB, "O5", p3, c3, d3, OrderStatus.CANCELLED, "BANK_TRANSFER");
        order(produkB, "O6", p2, c2, d2, OrderStatus.DELETE, "COD");

        otherProduk = produk(workspace("Workspace Lain"));
        order(otherProduk, "X1", p1, c1a, d1a1, OrderStatus.PAID, "COD");

        em.flush();
        em.clear();
    }

    @Test
    @DisplayName("Tanpa filter: owner melihat semua termasuk DELETE, admin tidak")
    void noFilter() {
        assertEquals(Set.of("O1", "O2", "O3", "O4", "O5", "O6"), list(OWNER, null, null, null, null, null));
        assertEquals(Set.of("O1", "O2", "O3", "O4", "O5"), list(ADMIN, null, null, null, null, null));
    }

    @Test
    @DisplayName("Beberapa provinsi (OR)")
    void multipleProvinces() {
        assertEquals(Set.of("O1", "O2", "O3", "O4", "O6"), list(OWNER, csv(p1, p2), null, null, null, null));
    }

    @Test
    @DisplayName("1 provinsi + beberapa kota")
    void oneProvinceMultipleCities() {
        assertEquals(Set.of("O1", "O2", "O3"), list(OWNER, csv(p1), csv(c1a, c1b), null, null, null));
    }

    @Test
    @DisplayName("1 kota + beberapa kecamatan")
    void oneCityMultipleDistricts() {
        assertEquals(Set.of("O1", "O2"), list(OWNER, csv(p1), csv(c1a), csv(d1a1, d1a2), null, null));
    }

    @Test
    @DisplayName("Beberapa status (OR)")
    void multipleStatuses() {
        assertEquals(Set.of("O2", "O3", "O4"), list(OWNER, null, null, null, "UNPAID,PENDING", null));
    }

    @Test
    @DisplayName("Kombinasi antar filter (AND)")
    void combinedFilters() {
        assertEquals(Set.of("O1", "O4"), list(OWNER, null, null, null, "PAID,UNPAID", "COD"));
        assertEquals(Set.of("O4"), list(OWNER, csv(p1, p2), null, null, "UNPAID", "COD"));
        assertEquals(Set.of("O2", "O5"), list(OWNER, null, null, null, null, "BANK_TRANSFER"));
    }

    @Test
    @DisplayName("Nilai tunggal memberi hasil seperti perilaku lama")
    void singleValues() {
        assertEquals(Set.of("O5"), list(OWNER, csv(p3), null, null, null, null));
        assertEquals(Set.of("O3"), list(OWNER, null, csv(c1b), null, null, null));
        assertEquals(Set.of("O1"), list(OWNER, null, null, null, "PAID", null));
    }

    @Test
    @DisplayName("Kota yang bukan bagian provinsi terpilih: hasil kosong")
    void mismatchedLocation() {
        assertEquals(Set.of(), list(OWNER, csv(p3), csv(c1a), null, null, null));
    }

    @Test
    @DisplayName("Non-owner dengan status DELETE,PAID: hanya PAID")
    void nonOwnerDeleteStatus() {
        assertEquals(Set.of("O1"), list(ADMIN, null, null, null, "DELETE,PAID", null));
        assertEquals(Set.of("O1", "O6"), list(OWNER, null, null, null, "DELETE,PAID", null));
    }

    // ── filter statusEkspor ──

    @Test
    @DisplayName("statusEkspor=false mencakup FALSE dan NULL; true hanya TRUE; null tanpa filter")
    void statusEksporFilter() {
        // Semua order seed berstatus_ekspor NULL (tanpa default di DB).
        em.getEntityManager().createNativeQuery(
                "UPDATE public.\"order\" SET status_ekspor = TRUE WHERE order_code = 'O1'").executeUpdate();
        em.getEntityManager().createNativeQuery(
                "UPDATE public.\"order\" SET status_ekspor = FALSE WHERE order_code = 'O2'").executeUpdate();

        assertEquals(Set.of("O2", "O3", "O4", "O5", "O6"), listEkspor(false));
        assertEquals(Set.of("O1"), listEkspor(true));
        assertEquals(Set.of("O1", "O2", "O3", "O4", "O5", "O6"), listEkspor(null));

        // Respons tidak pernah null: NULL di DB dikirim sebagai false.
        var rows = orderService.getOrderList(workspaceId, 1, 100, OrderListFilter.none(), null,
                null, null, null, null, "", false, "", OWNER).getContent();
        for (OrderListDto row : rows) {
            assertEquals(row.getOrderCode().equals("O1"), row.getStatusEkspor(), "order " + row.getOrderCode());
        }

        // Export memakai filter yang sama.
        var exported = orderRepository.exportOrder(workspaceId, "", "", "", "", "", "", false,
                null, null, null, null, "", "", "", "", false, true);
        assertEquals(Set.of("O2", "O3", "O4", "O5", "O6"),
                exported.stream().map(ExportOrderListDto::getOrderCode).collect(Collectors.toSet()));
    }

    private Set<String> listEkspor(Boolean statusEkspor) {
        return codes(orderService.getOrderList(workspaceId, 1, 100, OrderListFilter.none(), statusEkspor,
                null, null, null, null, "", false, "", OWNER));
    }

    // ── filter produk ──

    @Test
    @DisplayName("Produk: satu dan beberapa produk (OR)")
    void produkFilter() {
        assertEquals(Set.of("O1", "O2", "O3"), listProduk(OWNER, produkA.toString()));
        assertEquals(Set.of("O4", "O5", "O6"), listProduk(OWNER, produkB.toString()));
        assertEquals(Set.of("O1", "O2", "O3", "O4", "O5", "O6"), listProduk(OWNER, produkA + "," + produkB));
    }

    @Test
    @DisplayName("Produk: non-owner tetap tidak melihat order DELETE")
    void produkNonOwner() {
        assertEquals(Set.of("O4", "O5"), listProduk(ADMIN, produkB.toString()));
    }

    @Test
    @DisplayName("Produk: milik workspace lain atau tidak ada memberi hasil kosong")
    void produkOtherWorkspace() {
        assertEquals(Set.of(), listProduk(OWNER, otherProduk.toString()));
        assertEquals(Set.of(), listProduk(OWNER, UUID.randomUUID().toString()));
        // Dicampur dengan produk sendiri: hanya order produk sendiri yang tampil.
        assertEquals(Set.of("O1", "O2", "O3"), listProduk(OWNER, produkA + "," + otherProduk));
    }

    @Test
    @DisplayName("Produk dikombinasi dengan filter lain (AND)")
    void produkCombined() {
        OrderListFilter filter = OrderListFilterParser.parse(null, null, null, null, "COD", produkA.toString());
        assertEquals(Set.of("O1", "O3"), list(OWNER, filter));

        filter = OrderListFilterParser.parse(csv(p2), null, null, "UNPAID", null, produkB.toString());
        assertEquals(Set.of("O4"), list(OWNER, filter));

        filter = OrderListFilterParser.parse(csv(p3), null, null, null, null, produkA.toString());
        assertEquals(Set.of(), list(OWNER, filter));
    }

    @Test
    @DisplayName("Total list sama dengan jumlah baris export untuk filter yang sama")
    void listMatchesExport() {
        OrderListFilter filter = OrderListFilterParser.parse(csv(p1, p2), null, null, "UNPAID,PENDING,PAID", null, null);
        assertEquals(4, assertListMatchesExport(filter));

        filter = OrderListFilterParser.parse(null, null, null, null, null, produkA + "," + otherProduk);
        assertEquals(3, assertListMatchesExport(filter));
    }

    // ── helpers ──

    /** Membandingkan list dan export untuk filter yang sama; mengembalikan jumlah baris. */
    private long assertListMatchesExport(OrderListFilter filter) {
        Page<OrderListDto> page = orderService.getOrderList(workspaceId, 1, 100, filter, null,
                null, null, null, null, "", false, "", OWNER);
        var exported = orderRepository.exportOrder(workspaceId,
                filter.idProvinsiCsv(), filter.idKotaCsv(), filter.idKecamatanCsv(),
                filter.statusCsv(), filter.jenisPembayaranCsv(), filter.idProdukCsv(), null,
                null, null, null, null, "", "", "", "", false, true);

        assertEquals(page.getTotalElements(), exported.size());
        assertEquals(codes(page), exported.stream().map(ExportOrderListDto::getOrderCode).collect(Collectors.toSet()));
        return page.getTotalElements();
    }

    private Set<String> list(String user, String prov, String kota, String kec, String status, String bayar) {
        return list(user, OrderListFilterParser.parse(prov, kota, kec, status, bayar, null));
    }

    private Set<String> listProduk(String user, String idProduk) {
        return list(user, OrderListFilterParser.parse(null, null, null, null, null, idProduk));
    }

    private Set<String> list(String user, OrderListFilter filter) {
        return codes(orderService.getOrderList(workspaceId, 1, 100, filter, null,
                null, null, null, null, "", false, "", user));
    }

    private static Set<String> codes(Page<OrderListDto> page) {
        return page.getContent().stream().map(OrderListDto::getOrderCode).collect(Collectors.toSet());
    }

    private static String csv(Integer... ids) {
        return java.util.Arrays.stream(ids).map(String::valueOf).collect(Collectors.joining(","));
    }

    private void persistAccount(String username, Role role) {
        Account account = new Account();
        account.setUsername(username);
        account.setRole(role);
        account.setCreatedAt(Instant.now());
        em.persist(account);
    }

    private Long workspace(String nama) {
        Workspace workspace = new Workspace();
        workspace.setNamaWorkspace(nama);
        workspace.setCreatedAt(Instant.now());
        return em.persist(workspace).getId();
    }

    private UUID produk(Long idWorkspace) {
        Produk produk = new Produk();
        produk.setNamaProduk("Produk Test");
        produk.setUrlCheckout("produk-test-" + UUID.randomUUID());
        produk.setIdWorkspace(idWorkspace);
        return em.persist(produk).getId();
    }

    private Integer province(String nama) {
        Province province = new Province();
        province.setProvinceName(nama);
        return em.persist(province).getId();
    }

    private Integer city(String nama, Integer idProvince) {
        City city = new City();
        city.setCityName(nama);
        city.setIdProvince(idProvince);
        return em.persist(city).getId();
    }

    private Integer district(String nama, Integer idCity) {
        District district = new District();
        district.setDistrictName(nama);
        district.setIdCity(idCity);
        return em.persist(district).getId();
    }

    private void order(UUID idProduk, String code, Integer prov, Integer kota, Integer kec,
                       OrderStatus status, String pembayaran) {
        Order order = new Order();
        order.setIdProduk(idProduk);
        order.setOrderCode(code);
        order.setNamaPenerima("Pelanggan " + code);
        order.setNomorWhatsapp("62812" + code);
        order.setIdProvinsi(prov);
        order.setIdKota(kota);
        order.setIdKecamatan(kec);
        order.setStatus(status.name());
        order.setPembayaran(pembayaran);
        order.setHarga(100_000L);
        order.setOngkosKirim(10_000L);
        order.setBerat(500);
        order.setCreatedAt(Instant.now());
        em.persist(order);
    }
}
