package com.saktiform.api.service;

import com.saktiform.api.entity.Workspace;
import com.saktiform.api.model.product.ProdukListDto;
import com.saktiform.api.model.product.ProdukOrderStatistikView;
import com.saktiform.api.repository.*;
import com.saktiform.api.service.formconfig.ProdukFormConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit test {@link ProdukService#getProdukByWorkspace}: rentang tanggal order masuk
 * hanya mengganti angka statistik; tanpa rentang, counter produk dipakai apa adanya.
 */
class ProdukListStatistikServiceTest {

    private static final LocalDateTime AWAL = LocalDateTime.of(2026, 11, 1, 0, 0, 0);
    private static final LocalDateTime AKHIR = LocalDateTime.of(2026, 11, 30, 23, 59, 59);

    private ProdukRepository produkRepository;
    private ProdukService produkService;

    private final UUID produkA = UUID.randomUUID();
    private final UUID produkB = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        produkRepository = mock(ProdukRepository.class);
        WorkspaceRepository workspaceRepository = mock(WorkspaceRepository.class);
        produkService = new ProdukService(produkRepository, mock(FiturProdukRepository.class),
                mock(ProdukPembayaranRepository.class), mock(ProdukEkstraRepository.class),
                mock(ProdukTestimoniRepository.class), mock(GambarProdukRepository.class),
                mock(AtributProdukRepository.class), mock(GudangRepository.class),
                mock(ProdukIklanRepository.class), workspaceRepository,
                mock(StorageService.class), mock(DomainRepository.class), mock(ProdukFormConfigService.class));
        ReflectionTestUtils.setField(produkService, "checkoutUrl", "http://checkout");

        when(workspaceRepository.findById(1L)).thenReturn(Optional.of(new Workspace()));
    }

    /** Halaman berisi produk A (counter 100/50) dan B (counter 10/0). */
    private void halamanProduk() {
        ProdukListDto a = new ProdukListDto();
        a.setId(produkA);
        a.setUrlCheckout("a");
        a.applyStatistik(100L, 50L);
        ProdukListDto b = new ProdukListDto();
        b.setId(produkB);
        b.setUrlCheckout("b");
        b.applyStatistik(10L, 0L);
        when(produkRepository.findAllProdukListDto(eq(1L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(a, b)));
    }

    private static ProdukOrderStatistikView statistik(UUID id, long order, long dibayar) {
        return new ProdukOrderStatistikView() {
            public UUID getIdProduk() { return id; }
            public Long getTotalOrder() { return order; }
            public Long getTotalDibayar() { return dibayar; }
        };
    }

    @Test
    @DisplayName("Tanpa rentang: counter produk dipakai, query statistik tidak dipanggil")
    void withoutRange() {
        halamanProduk();

        var page = produkService.getProdukByWorkspace(1L, 1, 10, null, null, null);

        assertEquals(100L, page.getContent().get(0).getTotalOrder());
        assertEquals(50L, page.getContent().get(0).getTotalDibayar());
        verify(produkRepository, never()).getOrderStatistikByProduk(any(), any(), any());
    }

    @Test
    @DisplayName("Dengan rentang: statistik diganti; produk tanpa order di rentang menjadi 0")
    void withRange() {
        halamanProduk();
        when(produkRepository.getOrderStatistikByProduk(List.of(produkA, produkB), AWAL, AKHIR))
                .thenReturn(List.of(statistik(produkA, 4, 1)));

        var page = produkService.getProdukByWorkspace(1L, 1, 10, null, AWAL, AKHIR);

        ProdukListDto a = page.getContent().get(0);
        assertEquals(4L, a.getTotalOrder());
        assertEquals(1L, a.getTotalDibayar());
        assertEquals(1L, a.getTotalTerjual());
        assertEquals(String.format("%.2f%%", 25.0), a.getRasioDibayar());

        ProdukListDto b = page.getContent().get(1);
        assertEquals(0L, b.getTotalOrder());
        assertEquals(0L, b.getTotalDibayar());
        assertEquals("0%", b.getRasioDibayar());

        // Daftar produk tidak berubah: tetap dua produk dengan urutan sama.
        assertEquals(List.of(produkA, produkB), page.getContent().stream().map(ProdukListDto::getId).toList());
    }

    @Test
    @DisplayName("Rentang terbuka (hanya salah satu tanggal) tetap mengaktifkan statistik")
    void openRange() {
        halamanProduk();
        when(produkRepository.getOrderStatistikByProduk(any(), eq(AWAL), isNull())).thenReturn(List.of());

        var page = produkService.getProdukByWorkspace(1L, 1, 10, null, AWAL, null);

        assertEquals(0L, page.getContent().get(0).getTotalOrder());
        verify(produkRepository).getOrderStatistikByProduk(List.of(produkA, produkB), AWAL, null);
    }

    @Test
    @DisplayName("Tanggal awal setelah tanggal akhir ditolak")
    void invalidRange() {
        assertThrows(IllegalArgumentException.class,
                () -> produkService.getProdukByWorkspace(1L, 1, 10, null, AKHIR, AWAL));
        verifyNoInteractions(produkRepository);
    }
}
