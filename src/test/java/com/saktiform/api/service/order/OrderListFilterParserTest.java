package com.saktiform.api.service.order;

import com.saktiform.api.model.ErrorDto;
import com.saktiform.api.model.Order.OrderListFilter;
import com.saktiform.api.util.ValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

/** Unit test {@link OrderListFilterParser} sesuai docs/prd/order-multi-filter.md §5.2 dan §6. */
class OrderListFilterParserTest {

    private static List<String> codes(ValidationException e) {
        return e.getErrors().stream().map(err -> err.getField() + ":" + err.getCode()).toList();
    }

    private static ValidationException rejected(String prov, String kota, String kec, String status, String bayar) {
        return assertThrows(ValidationException.class,
                () -> OrderListFilterParser.parse(prov, kota, kec, status, bayar, null));
    }

    // ── parsing ──

    @Test
    @DisplayName("Param null atau kosong: tanpa filter")
    void emptyParams() {
        OrderListFilter filter = OrderListFilterParser.parse(null, "", "  ", null, ",,", null);
        assertEquals(OrderListFilter.none(), filter);
        assertEquals("", filter.idProvinsiCsv());
        assertEquals("", filter.statusCsv());
    }

    @Test
    @DisplayName("Nilai tunggal tetap bekerja seperti sebelumnya")
    void singleValue() {
        OrderListFilter filter = OrderListFilterParser.parse("32", null, null, "PAID", "COD", null);
        assertEquals(Set.of(32), filter.idProvinsi());
        assertEquals("PAID", filter.statusCsv());
        assertEquals("COD", filter.jenisPembayaranCsv());
    }

    @Test
    @DisplayName("Banyak nilai: spasi, token kosong & duplikat dibuang, enum tidak peka huruf")
    void multipleValues() {
        OrderListFilter filter = OrderListFilterParser.parse(
                " 33, 32,,32 ", null, null, "paid, Unpaid ,PAID", "cod,bank_transfer", null);
        assertEquals(Set.of(32, 33), filter.idProvinsi());
        assertEquals("32,33", filter.idProvinsiCsv());
        assertEquals(Set.of("PAID", "UNPAID"), filter.status());
        assertEquals(Set.of("COD", "BANK_TRANSFER"), filter.jenisPembayaran());
    }

    // ── V-1 / V-2: nilai tidak valid ──

    @Test
    @DisplayName("ID bukan bilangan bulat positif ditolak (V-1)")
    void invalidIds() {
        for (String bad : List.of("abc", "0", "-1", "1.5", "99999999999")) {
            ValidationException e = rejected(bad, null, null, null, null);
            assertEquals(List.of("idProvinsi:INVALID_FILTER_VALUE"), codes(e), "nilai: " + bad);
        }
    }

    @Test
    @DisplayName("Nilai enum tidak dikenal ditolak dan disebut dalam pesan (V-2)")
    void invalidEnums() {
        ValidationException e = rejected(null, null, null, "PAID,LUNAS", "COD,XYZ");
        assertEquals(List.of("status:INVALID_FILTER_VALUE", "jenisPembayaran:INVALID_FILTER_VALUE"), codes(e));
        assertTrue(e.getErrors().get(0).getMessage().contains("LUNAS"));
        assertTrue(e.getErrors().get(1).getMessage().contains("XYZ"));
    }

    // ── V-3: batas jumlah nilai ──

    @Test
    @DisplayName("Tepat 500 nilai lolos, 501 ditolak (V-3); duplikat tidak dihitung")
    void maxValues() {
        String ids500 = IntStream.rangeClosed(1, 500).mapToObj(String::valueOf).collect(Collectors.joining(","));
        assertEquals(500, OrderListFilterParser.parse(null, "1", ids500, null, null, null).idKecamatan().size());
        assertDoesNotThrow(() -> OrderListFilterParser.parse(null, "1", ids500 + ",1,2", null, null, null));

        ValidationException e = rejected(null, "1", ids500 + ",501", null, null);
        assertEquals(List.of("idKecamatan:FILTER_TOO_MANY_VALUES"), codes(e));
    }

    // ── V-4..V-6: lokasi bertingkat ──

    @Test
    @DisplayName(">=2 provinsi + kota ditolak (V-4)")
    void multiProvinceWithCity() {
        assertEquals(List.of("idKota:FILTER_LOCATION_CONFLICT"), codes(rejected("32,33", "3273", null, null, null)));
    }

    @Test
    @DisplayName(">=2 provinsi + kecamatan ditolak (V-5)")
    void multiProvinceWithDistrict() {
        assertEquals(List.of("idKecamatan:FILTER_LOCATION_CONFLICT"),
                codes(rejected("32,33", null, "327301", null, null)));
    }

    @Test
    @DisplayName(">=2 kota + kecamatan ditolak (V-6)")
    void multiCityWithDistrict() {
        assertEquals(List.of("idKecamatan:FILTER_LOCATION_CONFLICT"),
                codes(rejected("32", "3273,3204", "327301", null, null)));
    }

    @Test
    @DisplayName("Kombinasi lokasi yang tidak ambigu diterima")
    void allowedLocationCombinations() {
        assertDoesNotThrow(() -> OrderListFilterParser.parse("32,33", null, null, null, null, null));
        assertDoesNotThrow(() -> OrderListFilterParser.parse("32", "3273,3204", null, null, null, null));
        assertDoesNotThrow(() -> OrderListFilterParser.parse("32", "3273", "327301,327302", null, null, null));
        assertDoesNotThrow(() -> OrderListFilterParser.parse(null, "3273,3204", null, null, null, null)); // kota tanpa provinsi
        assertDoesNotThrow(() -> OrderListFilterParser.parse(null, null, "327301,327302", null, null, null)); // kecamatan tanpa kota
        // Duplikat dibuang sebelum dihitung: "32,32" = satu provinsi.
        assertDoesNotThrow(() -> OrderListFilterParser.parse("32,32", "3273", null, null, null, null));
    }

    // ── filter idProduk (docs/prd/order-product-filter.md) ──

    private static final String UUID_A = "3f2a1c4e-8b7d-4e6f-9a1b-2c3d4e5f6a7b";
    private static final String UUID_B = "0b1c2d3e-4f5a-4b6c-8d7e-9f0a1b2c3d4e";

    private static ValidationException rejectedProduk(String idProduk) {
        return assertThrows(ValidationException.class,
                () -> OrderListFilterParser.parse(null, null, null, null, null, idProduk));
    }

    @Test
    @DisplayName("Produk: kosong berarti tanpa filter")
    void produkEmpty() {
        for (String empty : java.util.Arrays.asList(null, "", "  ", ",,")) {
            OrderListFilter filter = OrderListFilterParser.parse(null, null, null, null, null, empty);
            assertEquals(OrderListFilter.none(), filter);
            assertEquals("", filter.idProdukCsv());
        }
    }

    @Test
    @DisplayName("Produk: banyak UUID, spasi & duplikat dibuang, huruf besar dinormalisasi")
    void produkMultipleValues() {
        OrderListFilter filter = OrderListFilterParser.parse(null, null, null, null, null,
                " " + UUID_A + " ,," + UUID_B + "," + UUID_A.toUpperCase());
        assertEquals(Set.of(UUID.fromString(UUID_A), UUID.fromString(UUID_B)), filter.idProduk());
        assertEquals(UUID_B + "," + UUID_A, filter.idProdukCsv());
    }

    @Test
    @DisplayName("Produk: token bukan UUID kanonik ditolak dan disebut dalam pesan")
    void produkInvalid() {
        for (String bad : List.of("abc", "123", "1-1-1-1-1", UUID_A + "x", UUID_A.replace("-", ""))) {
            ValidationException e = rejectedProduk(UUID_B + "," + bad);
            assertEquals(List.of("idProduk:INVALID_FILTER_VALUE"), codes(e), "nilai: " + bad);
            assertTrue(e.getErrors().get(0).getMessage().contains(bad), "nilai: " + bad);
        }
    }

    @Test
    @DisplayName("Produk: tepat 100 UUID lolos, 101 ditolak; duplikat tidak dihitung")
    void produkMaxValues() {
        List<String> ids = IntStream.rangeClosed(1, 101)
                .mapToObj(i -> new UUID(0L, i).toString()).toList();
        String ids100 = String.join(",", ids.subList(0, 100));

        assertEquals(100, OrderListFilterParser.parse(null, null, null, null, null, ids100).idProduk().size());
        assertDoesNotThrow(() -> OrderListFilterParser.parse(null, null, null, null, null, ids100 + "," + ids.get(0)));

        ValidationException e = rejectedProduk(ids100 + "," + ids.get(100));
        assertEquals(List.of("idProduk:FILTER_TOO_MANY_VALUES"), codes(e));
        assertTrue(e.getErrors().get(0).getMessage().contains("100"));
    }

    @Test
    @DisplayName("Produk: digabung dengan filter lain tanpa konflik")
    void produkCombined() {
        OrderListFilter filter = OrderListFilterParser.parse("32,33", null, null, "PAID", "COD", UUID_A);
        assertEquals(Set.of(32, 33), filter.idProvinsi());
        assertEquals(Set.of(UUID.fromString(UUID_A)), filter.idProduk());
    }

    // ── filter jenisPembayaran satu nilai (chat) ──

    @Test
    @DisplayName("Chat: kosong berarti tanpa filter, enum tidak peka huruf")
    void singlePaymentFilter() {
        assertNull(OrderListFilterParser.parseSingleJenisPembayaran(null));
        assertNull(OrderListFilterParser.parseSingleJenisPembayaran(""));
        assertNull(OrderListFilterParser.parseSingleJenisPembayaran("  "));
        assertEquals("COD", OrderListFilterParser.parseSingleJenisPembayaran(" cod "));
        assertEquals("BANK_TRANSFER", OrderListFilterParser.parseSingleJenisPembayaran("bank_transfer"));
        assertEquals("COD", OrderListFilterParser.parseSingleJenisPembayaran("COD,cod")); // duplikat digabung
    }

    @Test
    @DisplayName("Chat: nilai tidak dikenal atau lebih dari satu ditolak")
    void singlePaymentFilterRejected() {
        for (String bad : List.of("Bank Transfer", "TRANSFER", "COD,BANK_TRANSFER")) {
            ValidationException e = assertThrows(ValidationException.class,
                    () -> OrderListFilterParser.parseSingleJenisPembayaran(bad), "nilai: " + bad);
            assertEquals(List.of("jenisPembayaran:INVALID_FILTER_VALUE"), codes(e), "nilai: " + bad);
        }
    }

    @Test
    @DisplayName("Semua pelanggaran dilaporkan sekaligus")
    void allErrorsAtOnce() {
        ValidationException e = rejected("32,33,abc", "3273", "327301", "LUNAS", null);
        assertEquals(List.of(
                "idProvinsi:INVALID_FILTER_VALUE",
                "status:INVALID_FILTER_VALUE",
                "idKota:FILTER_LOCATION_CONFLICT",
                "idKecamatan:FILTER_LOCATION_CONFLICT"), codes(e));
        assertEquals(e.getErrors().get(0).getMessage(), e.getMessage());
        assertTrue(e.getErrors().stream().map(ErrorDto::getMessage).allMatch(m -> m != null && !m.isBlank()));
    }
}
