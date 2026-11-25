package com.saktiform.api.service.order;

import com.saktiform.api.entity.*;
import com.saktiform.api.model.Order.CreateOrderDto;
import com.saktiform.api.model.Order.ExportOrderListDto;
import com.saktiform.api.model.Order.OrderStatus;
import com.saktiform.api.model.Order.UpdateOrderDto;
import com.saktiform.api.model.account.Role;
import com.saktiform.api.repository.*;
import com.saktiform.api.service.formconfig.OrderCustomFieldService;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
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

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;

/**
 * Integration test biaya COD pada {@link OrderService} terhadap PostgreSQL asli
 * (Testcontainers). Lihat docs/prd/cod-fee.md §11.
 *
 * <p>Data: harga varian 100.000, tarif ongkir 20.000, config COD produk
 * {@code hanya-produk 3%, min 2.000, maks 5.000}.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class CodFeeOrderIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final String ACTOR = "owner-test";
    private static final long HARGA = 100_000L;
    private static final long TARIF = 20_000L;

    @Autowired private TestEntityManager em;
    @Autowired private OrderRepository orderRepository;
    @Autowired private OngkirRepository ongkirRepository;
    @Autowired private GudangRepository gudangRepository;
    @Autowired private ProdukRepository produkRepository;
    @Autowired private ProdukPembayaranRepository produkPembayaranRepository;
    @Autowired private AtributProdukRepository atributProdukRepository;
    @Autowired private AbandonedOrderRepository abandonedOrderRepository;
    @Autowired private ContactRepository contactRepository;
    @Autowired private ProvinceRepository provinceRepository;
    @Autowired private CityRepository cityRepository;
    @Autowired private DistrictRepository districtRepository;
    @Autowired private AccountRepository accountRepository;
    @Autowired private OrderSequenceRepository orderSequenceRepository;
    @Autowired private OrderHistoryRepository orderHistoryRepository;

    private OrderService orderService;

    private Long workspaceId;
    private Integer provinsiId;
    private Integer kotaId;
    private Integer kecamatanId;
    private UUID produkId;
    private UUID atributId;
    private Long codPembayaranId;

    @BeforeEach
    void setUp() {
        orderService = new OrderService(orderRepository, ongkirRepository, gudangRepository, produkRepository,
                produkPembayaranRepository, atributProdukRepository, abandonedOrderRepository,
                contactRepository, provinceRepository, cityRepository, districtRepository,
                accountRepository, orderSequenceRepository, orderHistoryRepository,
                mock(OrderCustomFieldService.class));

        Account owner = new Account();
        owner.setUsername(ACTOR);
        owner.setRole(Role.OWNER);
        owner.setCreatedAt(Instant.now());
        em.persist(owner);

        Workspace workspace = new Workspace();
        workspace.setNamaWorkspace("Workspace Test");
        workspace.setCreatedAt(Instant.now());
        workspaceId = em.persist(workspace).getId();

        Province province = new Province();
        province.setProvinceName("Jawa Barat");
        provinsiId = em.persist(province).getId();
        City city = new City();
        city.setCityName("Bandung");
        city.setIdProvince(provinsiId);
        kotaId = em.persist(city).getId();
        District district = new District();
        district.setDistrictName("Coblong");
        district.setIdCity(kotaId);
        kecamatanId = em.persist(district).getId();

        Gudang gudang = new Gudang();
        gudang.setNamaGudang("Gudang");
        gudang.setIdProvinsi(provinsiId);
        gudang.setIdKota(kotaId);
        gudang.setIdKecamatan(kecamatanId);
        gudang.setIdWorkspace(workspaceId);
        Long gudangId = em.persist(gudang).getId();

        Ongkir ongkir = new Ongkir();
        ongkir.setIdOriginCity(kotaId);
        ongkir.setIdDistrict(kecamatanId);
        ongkir.setOngkirValue(BigDecimal.valueOf(TARIF));
        em.persist(ongkir);

        Produk produk = new Produk();
        produk.setNamaProduk("Produk Test");
        produk.setUrlCheckout("produk-test-" + UUID.randomUUID());
        produk.setIdWorkspace(workspaceId);
        produk.setIdGudang(gudangId);
        produk.setOrderCount(0L);
        produk.setSoldCount(0L);
        produkId = em.persist(produk).getId();

        AtributProduk atribut = new AtributProduk();
        atribut.setIdProduk(produkId);
        atribut.setDeskripsi("1 Pcs");
        atribut.setHarga(HARGA);
        atribut.setBerat(500);
        atributId = em.persist(atribut).getId();

        codPembayaranId = persistPembayaran("COD", codConfig("hanya-produk", 3, 2000, 5000));
        persistPembayaran("BANK_TRANSFER", Map.of("deskripsi", "BCA 123"));

        em.flush();
        em.clear();
    }

    // ── create ──

    @Test
    @DisplayName("Create COD: biaya_cod terisi, ongkos_kirim = tarif + biaya")
    void createCod() {
        Order order = reload(createOrder("COD"));
        assertEquals(3_000L, order.getBiayaCod());       // 3% x 100.000
        assertEquals(TARIF + 3_000L, order.getOngkosKirim());
    }

    @Test
    @DisplayName("Create transfer: biaya_cod = 0, ongkos_kirim = tarif")
    void createTransfer() {
        Order order = reload(createOrder("BANK_TRANSFER"));
        assertEquals(0L, order.getBiayaCod());
        assertEquals(TARIF, order.getOngkosKirim());
    }

    @Test
    @DisplayName("Order agen dengan diskon: dihitung dari harga setelah diskon, tidak terhitung dua kali")
    void agentDiscountRecalculation() {
        Order order = orderService.createOrderInternal(createDto("COD", "CST_CHAT"), ACTOR, null);
        // Meniru OrderOrchestrationService.createOrderOnChat.
        order.setDiskon(50_000L);
        orderService.recalculateCodFee(order);
        em.flush();

        Order saved = reload(order.getId());
        assertEquals(2_000L, saved.getBiayaCod());       // 3% x 50.000 = 1.500 -> minimal 2.000
        assertEquals(TARIF + 2_000L, saved.getOngkosKirim());
    }

    // ── update ──

    @Test
    @DisplayName("Update order baru: diskon berubah, biaya dihitung ulang dari tarif murni")
    void updateRecalculates() {
        UUID id = createOrder("COD");

        orderService.updateOrder(updateDto(id, "COD", 20_000L), ACTOR);
        em.flush();

        Order order = reload(id);
        assertEquals(2_400L, order.getBiayaCod());       // 3% x 80.000
        assertEquals(TARIF + 2_400L, order.getOngkosKirim());
    }

    @Test
    @DisplayName("Update setelah config produk diubah: snapshot order dipakai, biaya tidak berubah")
    void updateUsesSnapshot() {
        UUID id = createOrder("COD");
        ProdukPembayaran cod = em.find(ProdukPembayaran.class, codPembayaranId);
        cod.setConfig(codConfig("hanya-produk", 10, null, null));
        em.flush();
        em.clear();

        orderService.updateOrder(updateDto(id, "COD", 0L), ACTOR);
        em.flush();

        assertEquals(3_000L, reload(id).getBiayaCod());
    }

    @Test
    @DisplayName("Update ganti metode transfer -> COD: config produk terbaru dipakai")
    void updateChangePaymentMethod() {
        UUID id = createOrder("BANK_TRANSFER");
        ProdukPembayaran cod = em.find(ProdukPembayaran.class, codPembayaranId);
        cod.setConfig(codConfig("hanya-pengiriman", 10, null, null));
        em.flush();
        em.clear();

        orderService.updateOrder(updateDto(id, "COD", 0L), ACTOR);
        em.flush();

        Order order = reload(id);
        assertEquals("COD", order.getPembayaran());
        assertEquals(2_000L, order.getBiayaCod());       // 10% x tarif 20.000
        assertEquals(TARIF + 2_000L, order.getOngkosKirim());
    }

    @Test
    @DisplayName("Update order lama (biaya_cod NULL): tetap NULL, ongkos_kirim = tarif")
    void updateLegacyOrder() {
        UUID id = createOrder("COD");
        markAsLegacy(id);

        orderService.updateOrder(updateDto(id, "COD", 0L), ACTOR);
        em.flush();

        Order order = reload(id);
        assertNull(order.getBiayaCod());
        assertEquals(TARIF, order.getOngkosKirim());
    }

    // ── export ──

    @Test
    @DisplayName("Export: original_shipping_cost = tarif murni, termasuk order lama")
    void exportOriginalShippingCost() throws Exception {
        UUID cod = createOrder("COD");
        UUID legacy = createOrder("COD");
        markAsLegacy(legacy);

        List<ExportOrderListDto> rows = orderRepository.exportOrder(workspaceId, "", "", "", "", "", "",
                null, null, null, null, null, "", "", "", "", false, true);
        assertEquals(2, rows.size());

        Map<String, String[]> cells = readExport(orderService.generateUserExcel(rows));
        String codCode = reload(cod).getOrderCode();
        String legacyCode = reload(legacy).getOrderCode();

        // kolom 16 = shipping_cost, kolom 25 = original_shipping_cost
        assertEquals(String.valueOf(TARIF + 3_000L), cells.get(codCode)[16]);
        assertEquals(String.valueOf(TARIF), cells.get(codCode)[25]);
        assertEquals(String.valueOf(TARIF), cells.get(legacyCode)[16]);
        assertEquals(String.valueOf(TARIF), cells.get(legacyCode)[25]);
    }

    // ── helpers ──

    private UUID createOrder(String metode) {
        Order order = orderService.createOrderInternal(createDto(metode, "FORM"), ACTOR, "127.0.0.1");
        em.flush();
        em.clear();
        return order.getId();
    }

    /** Meniru order yang dibuat sebelum fitur: biaya_cod NULL, ongkos_kirim = tarif. */
    private void markAsLegacy(UUID id) {
        Order order = em.find(Order.class, id);
        order.setBiayaCod(null);
        order.setOngkosKirim(TARIF);
        em.flush();
        em.clear();
    }

    private CreateOrderDto createDto(String metode, String source) {
        return new CreateOrderDto(produkId, atributId, "Budi", "081234567890", "Jl. Test",
                provinsiId, kotaId, kecamatanId, metode, source, null);
    }

    private UpdateOrderDto updateDto(UUID id, String metode, Long diskon) {
        UpdateOrderDto dto = new UpdateOrderDto();
        dto.setId(id);
        dto.setIdProduk(produkId);
        dto.setIdAtributProduk(atributId);
        dto.setNamaLengkap("Budi");
        dto.setNomorWhatsapp("081234567890");
        dto.setAlamat("Jl. Test");
        dto.setIdProvinsi(provinsiId);
        dto.setIdKota(kotaId);
        dto.setIdKecamatan(kecamatanId);
        dto.setMetodePembayaran(metode);
        dto.setDiskon(diskon);
        dto.setStatus(OrderStatus.UNPAID);
        return dto;
    }

    private Order reload(UUID id) {
        em.clear();
        return em.find(Order.class, id);
    }

    private Long persistPembayaran(String tipe, Map<String, Object> config) {
        ProdukPembayaran pembayaran = new ProdukPembayaran();
        pembayaran.setIdProduk(produkId);
        pembayaran.setPembayaran(tipe);
        pembayaran.setConfig(new HashMap<>(config));
        pembayaran.setCreatedAt(Instant.now());
        return em.persist(pembayaran).getId();
    }

    private static Map<String, Object> codConfig(String tipe, Object persen, Object min, Object max) {
        Map<String, Object> config = new HashMap<>();
        config.put("tipeBiaya", tipe);
        config.put("persentaseBiaya", persen);
        if (min != null) config.put("minimalBiaya", min);
        if (max != null) config.put("maksimalBiaya", max);
        return config;
    }

    /** Membaca sheet export menjadi map orderCode -> nilai sel (sebagai string). */
    private static Map<String, String[]> readExport(byte[] xlsx) throws Exception {
        Map<String, String[]> result = new HashMap<>();
        try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
            var sheet = workbook.getSheetAt(0);
            for (Row row : sheet) {
                if (row.getRowNum() == 0) continue;
                String[] values = new String[row.getLastCellNum()];
                for (int i = 0; i < values.length; i++) {
                    var cell = row.getCell(i);
                    values[i] = cell == null ? null : cell.toString();
                }
                result.put(values[0], values);
            }
        }
        return result;
    }
}
