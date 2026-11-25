package com.saktiform.api.service;

import com.saktiform.api.model.product.ProdukListDropdown;
import com.saktiform.api.repository.*;
import com.saktiform.api.service.formconfig.ProdukFormConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Unit test {@link ProdukService#getProdukListDropdown}: pemilihan bentuk respons & validasi paging. */
class ProdukDropdownServiceTest {

    private ProdukRepository produkRepository;
    private ProdukService produkService;

    @BeforeEach
    void setUp() {
        produkRepository = mock(ProdukRepository.class);
        produkService = new ProdukService(produkRepository, mock(FiturProdukRepository.class),
                mock(ProdukPembayaranRepository.class), mock(ProdukEkstraRepository.class),
                mock(ProdukTestimoniRepository.class), mock(GambarProdukRepository.class),
                mock(AtributProdukRepository.class), mock(GudangRepository.class),
                mock(ProdukIklanRepository.class), mock(WorkspaceRepository.class),
                mock(StorageService.class), mock(DomainRepository.class), mock(ProdukFormConfigService.class));
    }

    @Test
    @DisplayName("Tanpa page: List penuh (bentuk lama), search null menjadi ''")
    void withoutPageReturnsList() {
        when(produkRepository.findAllProdukListDropdown(eq(1L), eq(""), any(Sort.class))).thenReturn(List.of());

        Object result = produkService.getProdukListDropdown(1L, null, null, null);

        assertInstanceOf(List.class, result);
        verify(produkRepository).findAllProdukListDropdown(1L, "", Sort.by("namaProduk", "id"));
        verify(produkRepository, never()).findPageProdukListDropdown(anyLong(), anyString(), any());
    }

    @Test
    @DisplayName("Search di-trim sebelum diteruskan")
    void searchIsTrimmed() {
        produkService.getProdukListDropdown(1L, "  Kopi ", null, null);
        verify(produkRepository).findAllProdukListDropdown(eq(1L), eq("Kopi"), any(Sort.class));
    }

    @Test
    @DisplayName("Dengan page: Page, page 1-based, limit default 10, urut nama lalu id")
    void withPageReturnsPage() {
        Page<ProdukListDropdown> page = new PageImpl<>(List.of());
        when(produkRepository.findPageProdukListDropdown(eq(1L), eq("kopi"), any(Pageable.class))).thenReturn(page);

        Object result = produkService.getProdukListDropdown(1L, "kopi", 2, null);

        assertSame(page, result);
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(produkRepository).findPageProdukListDropdown(eq(1L), eq("kopi"), captor.capture());
        assertEquals(1, captor.getValue().getPageNumber());
        assertEquals(10, captor.getValue().getPageSize());
        assertEquals(Sort.by("namaProduk", "id"), captor.getValue().getSort());
    }

    @Test
    @DisplayName("Limit 100 diterima")
    void maxLimitAccepted() {
        assertDoesNotThrow(() -> produkService.getProdukListDropdown(1L, null, 1, ProdukService.MAX_DROPDOWN_LIMIT));
    }

    @Test
    @DisplayName("page < 1 atau limit di luar 1..100 ditolak")
    void invalidPaging() {
        assertThrows(IllegalArgumentException.class, () -> produkService.getProdukListDropdown(1L, null, 0, 10));
        assertThrows(IllegalArgumentException.class, () -> produkService.getProdukListDropdown(1L, null, 1, 0));
        assertThrows(IllegalArgumentException.class, () -> produkService.getProdukListDropdown(1L, null, 1, 101));
        verifyNoInteractions(produkRepository);
    }
}
