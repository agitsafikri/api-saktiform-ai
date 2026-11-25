package com.saktiform.api.service.order;

import com.saktiform.api.model.ErrorDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Unit test {@link CodFeeCalculator} sesuai docs/prd/cod-fee.md §5 dan §8. */
class CodFeeCalculatorTest {

    private static Map<String, Object> config(Object tipe, Object persen, Object min, Object max) {
        Map<String, Object> config = new HashMap<>();
        if (tipe != null) config.put("tipeBiaya", tipe);
        if (persen != null) config.put("persentaseBiaya", persen);
        if (min != null) config.put("minimalBiaya", min);
        if (max != null) config.put("maksimalBiaya", max);
        return config;
    }

    // ── §5.4: harga 100.000, diskon 10.000, ongkir 20.000, 3%, min 2.000, maks 5.000 ──

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
            "hanya-produk,      2700",
            "produk-pengiriman, 3300",
            "hanya-pengiriman,  2000", // 600 -> minimal 2.000
    })
    @DisplayName("Contoh dokumen §5.4 untuk ketiga tipe")
    void documentExamples(String tipe, long expected) {
        long biaya = CodFeeCalculator.calculate("COD", config(tipe, 3, 2000, 5000), 100_000L, 10_000L, 20_000L);
        assertEquals(expected, biaya);
    }

    @Test
    @DisplayName("tipeBiaya dicocokkan case-insensitive")
    void tipeCaseInsensitive() {
        assertEquals(2700, CodFeeCalculator.calculate("cod", config(" Hanya-Produk ", 3, null, null),
                100_000L, 10_000L, 20_000L));
    }

    // ── pembulatan ke atas ke kelipatan 100 ──

    @ParameterizedTest(name = "harga {0} x 3% -> {1}")
    @CsvSource({
            "137583, 4200", // 4.127,49 -> 4.200
            "140000, 4200", // tepat 4.200
            "140001, 4300", // 4.200,03 -> 4.300
            "1,      100",  // 0,03 -> 100
            "0,      0",
    })
    @DisplayName("Dibulatkan ke atas ke kelipatan Rp100")
    void roundUpToHundreds(long harga, long expected) {
        assertEquals(expected, CodFeeCalculator.calculate("COD", config("hanya-produk", 3, null, null),
                harga, 0L, 0L));
    }

    @Test
    @DisplayName("Pembulatan sebelum batas maks: maks bukan kelipatan 100 tetap dihormati")
    void roundingBeforeMax() {
        // 4.127,49 -> 4.200 -> maks 4.150
        assertEquals(4150, CodFeeCalculator.calculate("COD", config("hanya-produk", 3, null, 4150),
                137_583L, 0L, 0L));
    }

    // ── min / maks opsional ──

    @Test
    @DisplayName("Tanpa min & maks: tidak dibatasi")
    void noBounds() {
        assertEquals(30_000, CodFeeCalculator.calculate("COD", config("hanya-produk", 3, null, null),
                1_000_000L, 0L, 0L));
    }

    @Test
    @DisplayName("Hanya minimal")
    void onlyMinimal() {
        assertEquals(5_000, CodFeeCalculator.calculate("COD", config("hanya-produk", 3, 5000, null),
                10_000L, 0L, 0L));
    }

    @Test
    @DisplayName("Hanya maksimal")
    void onlyMaksimal() {
        assertEquals(10_000, CodFeeCalculator.calculate("COD", config("hanya-produk", 3, null, 10000),
                1_000_000L, 0L, 0L));
    }

    @Test
    @DisplayName("String kosong pada min/maks dianggap tidak diisi")
    void blankBounds() {
        assertEquals(300, CodFeeCalculator.calculate("COD", config("hanya-produk", 3, "", " "),
                10_000L, 0L, 0L));
    }

    // ── kasus biaya 0 ──

    @Test
    @DisplayName("Diskon lebih besar dari harga: dasar dibatasi 0")
    void discountExceedsPrice() {
        assertEquals(0, CodFeeCalculator.calculate("COD", config("hanya-produk", 3, null, null),
                10_000L, 20_000L, 0L));
    }

    @Test
    @DisplayName("Metode transfer, config null/kosong, persen 0: biaya 0")
    void zeroCases() {
        Map<String, Object> valid = config("hanya-produk", 3, 1000, null);
        assertEquals(0, CodFeeCalculator.calculate("BANK_TRANSFER", valid, 100_000L, 0L, 0L));
        assertEquals(0, CodFeeCalculator.calculate(null, valid, 100_000L, 0L, 0L));
        assertEquals(0, CodFeeCalculator.calculate("COD", null, 100_000L, 0L, 0L));
        assertEquals(0, CodFeeCalculator.calculate("COD", Map.of(), 100_000L, 0L, 0L));
        assertEquals(0, CodFeeCalculator.calculate("COD", Map.of("deskripsi", "Bayar di tempat"), 100_000L, 0L, 0L));
        assertEquals(0, CodFeeCalculator.calculate("COD", config("hanya-produk", 0, null, null), 100_000L, 0L, 0L));
    }

    @Test
    @DisplayName("Config tidak valid saat dihitung: biaya 0, tidak melempar exception")
    void invalidConfigAtRuntime() {
        assertEquals(0, CodFeeCalculator.calculate("COD", config("tidak-dikenal", 3, null, null), 100_000L, 0L, 0L));
        assertEquals(0, CodFeeCalculator.calculate("COD", config("hanya-produk", "abc", null, null), 100_000L, 0L, 0L));
        assertEquals(0, CodFeeCalculator.calculate("COD", config("hanya-produk", 3, "abc", null), 100_000L, 0L, 0L));
        assertEquals(0, CodFeeCalculator.calculate("COD", config(null, 3, null, null), 100_000L, 0L, 0L));
    }

    // ── parsing angka ──

    @Test
    @DisplayName("Parsing toleran: Number maupun string numerik")
    void lenientParsing() {
        assertEquals(2700, CodFeeCalculator.calculate("COD", config("hanya-produk", "3", "2500.0", 5000.0),
                90_000L, 0L, 0L));
        assertEquals(2300, CodFeeCalculator.calculate("COD", config("hanya-produk", "2.5", null, null),
                90_000L, 0L, 0L)); // 2.250 -> 2.300
    }

    @Test
    @DisplayName("Null harga/diskon/ongkir dianggap 0")
    void nullAmounts() {
        assertEquals(0, CodFeeCalculator.calculate("COD", config("produk-pengiriman", 3, null, null), null, null, null));
    }

    // ── validasi saat produk disimpan ──

    @Test
    @DisplayName("Validasi: config valid & config tanpa pengaturan biaya tidak menghasilkan error")
    void validationOk() {
        assertTrue(CodFeeCalculator.validateConfig("c", config("hanya-pengiriman", "2.5", "1000", 5000)).isEmpty());
        assertTrue(CodFeeCalculator.validateConfig("c", config("hanya-produk", 3, null, null)).isEmpty());
        assertTrue(CodFeeCalculator.validateConfig("c", null).isEmpty());
        assertTrue(CodFeeCalculator.validateConfig("c", Map.of("deskripsi", "COD")).isEmpty());
    }

    @Test
    @DisplayName("Validasi: setiap pelanggaran dilaporkan sekaligus")
    void validationErrors() {
        List<ErrorDto> errors = CodFeeCalculator.validateConfig("pembayaran[0].config",
                config("salah", 150, -1, "1.5"));
        List<String> fields = errors.stream().map(ErrorDto::getField).toList();
        assertEquals(List.of(
                "pembayaran[0].config.tipeBiaya",
                "pembayaran[0].config.persentaseBiaya",
                "pembayaran[0].config.minimalBiaya",
                "pembayaran[0].config.maksimalBiaya"), fields);
    }

    // ── copy produk ──

    @Test
    @DisplayName("Copy: config COD tidak valid -> key biaya dibuang, key lain tetap, asli tidak diubah")
    void sanitizeInvalidForCopy() {
        Map<String, Object> original = config("hanya-produk", null, 2000, 5000); // persen hilang
        original.put("deskripsi", "Bayar di tempat");

        Map<String, Object> copied = CodFeeCalculator.sanitizeForCopy("COD", original);

        assertEquals(Map.of("deskripsi", "Bayar di tempat"), copied);
        assertEquals(4, original.size(), "config produk asal tidak boleh berubah");
        assertTrue(CodFeeCalculator.validateConfig("c", copied).isEmpty());
    }

    @Test
    @DisplayName("Copy: config valid, bukan COD, atau null -> dikembalikan apa adanya")
    void sanitizeKeepsValidOrOther() {
        Map<String, Object> valid = config("hanya-pengiriman", "2.5", null, null);
        assertSame(valid, CodFeeCalculator.sanitizeForCopy("cod", valid));

        Map<String, Object> transfer = config("salah", 999, null, null); // tidak divalidasi untuk transfer
        assertSame(transfer, CodFeeCalculator.sanitizeForCopy("BANK_TRANSFER", transfer));

        Map<String, Object> noFee = Map.of("deskripsi", "COD");
        assertSame(noFee, CodFeeCalculator.sanitizeForCopy("COD", noFee));

        assertNull(CodFeeCalculator.sanitizeForCopy("COD", null));
    }

    @Test
    @DisplayName("Validasi: persen wajib, min tidak boleh > maks")
    void validationMissingPercentAndMinGreaterThanMax() {
        List<String> fields = CodFeeCalculator.validateConfig("c", config("hanya-produk", null, 5000, 1000))
                .stream().map(ErrorDto::getField).toList();
        assertEquals(List.of("c.persentaseBiaya", "c.minimalBiaya"), fields);
    }
}
