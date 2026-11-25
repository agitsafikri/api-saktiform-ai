package com.saktiform.api.model.product;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Unit test perhitungan statistik {@link ProdukListDto}. */
class ProdukListDtoTest {

    private static ProdukListDto dto(Long totalOrder, Long totalDibayar) {
        return new ProdukListDto(UUID.randomUUID(), "Produk", 10_000L, totalOrder, totalDibayar, "url");
    }

    @Test
    @DisplayName("Counter NULL (produk lama) tidak melempar NPE dan dianggap 0")
    void nullCounters() {
        ProdukListDto dto = assertDoesNotThrow(() -> dto(null, null));
        assertEquals(0L, dto.getTotalOrder());
        assertEquals(0L, dto.getTotalDibayar());
        assertEquals(0L, dto.getTotalTerjual());
        assertEquals("0%", dto.getRasioDibayar());
    }

    @Test
    @DisplayName("Rasio dihitung dua desimal; totalTerjual = totalDibayar")
    void ratio() {
        ProdukListDto dto = dto(3L, 1L);
        assertEquals(String.format("%.2f%%", 100.0 / 3), dto.getRasioDibayar());
        assertEquals(1L, dto.getTotalTerjual());
        assertEquals(String.format("%.2f%%", 100.0), dto(4L, 4L).getRasioDibayar());
    }

    @Test
    @DisplayName("applyStatistik menimpa seluruh angka statistik")
    void applyStatistikOverrides() {
        ProdukListDto dto = dto(100L, 50L);
        dto.applyStatistik(4L, 1L);
        assertEquals(4L, dto.getTotalOrder());
        assertEquals(1L, dto.getTotalDibayar());
        assertEquals(1L, dto.getTotalTerjual());
        assertEquals(String.format("%.2f%%", 25.0), dto.getRasioDibayar());

        dto.applyStatistik(0L, 0L);
        assertEquals("0%", dto.getRasioDibayar());
    }
}
