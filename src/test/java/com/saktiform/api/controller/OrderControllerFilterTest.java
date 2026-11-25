package com.saktiform.api.controller;

import com.saktiform.api.configuration.JwtManager;
import com.saktiform.api.model.Order.OrderListFilter;
import com.saktiform.api.service.order.OrderOrchestrationService;
import com.saktiform.api.service.order.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Test kontrak HTTP filter multi-nilai pada {@link OrderController}
 * (docs/prd/order-multi-filter.md §6.2): 400 berformat RestResponse + ErrorDto
 * untuk list maupun export, dan param koma diteruskan sebagai {@link OrderListFilter}.
 */
class OrderControllerFilterTest {

    private OrderService orderService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        orderService = mock(OrderService.class);
        JwtManager jwtManager = mock(JwtManager.class);
        when(jwtManager.getUsernameByToken(anyString())).thenReturn("owner");
        mockMvc = MockMvcBuilders.standaloneSetup(
                new OrderController(orderService, jwtManager, mock(OrderOrchestrationService.class))).build();
    }

    @ParameterizedTest
    @ValueSource(strings = {"/order", "/order/export"})
    @DisplayName("Kombinasi lokasi terlarang: 400 RestResponse dengan seluruh ErrorDto")
    void locationConflict(String path) throws Exception {
        mockMvc.perform(get(path)
                        .header("Authorization", "Bearer token")
                        .param("workspaceId", "1")
                        .param("idProvinsi", "32,33")
                        .param("idKota", "3273")
                        .param("status", "LUNAS"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].field").value("status"))
                .andExpect(jsonPath("$.data[0].code").value("INVALID_FILTER_VALUE"))
                .andExpect(jsonPath("$.data[1].field").value("idKota"))
                .andExpect(jsonPath("$.data[1].code").value("FILTER_LOCATION_CONFLICT"));

        verifyNoInteractions(orderService);
    }

    @Test
    @DisplayName("Param koma diteruskan ke service sebagai OrderListFilter")
    void passesParsedFilter() throws Exception {
        when(orderService.getOrderList(anyLong(), anyInt(), anyInt(), any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any())).thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 10), 0));

        mockMvc.perform(get("/order")
                        .header("Authorization", "Bearer token")
                        .param("workspaceId", "1")
                        .param("idProvinsi", "32")
                        .param("idKota", "3273,3204")
                        .param("status", "paid,UNPAID")
                        .param("jenisPembayaran", "COD"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        ArgumentCaptor<OrderListFilter> captor = ArgumentCaptor.forClass(OrderListFilter.class);
        verify(orderService).getOrderList(eq(1L), eq(1), eq(10), captor.capture(), any(), any(), any(), any(),
                any(), any(), any(), any(), eq("owner"));
        OrderListFilter filter = captor.getValue();
        assertEquals(Set.of(32), filter.idProvinsi());
        assertEquals(Set.of(3273, 3204), filter.idKota());
        assertEquals(Set.of("PAID", "UNPAID"), filter.status());
        assertEquals(Set.of("COD"), filter.jenisPembayaran());
    }

    @Test
    @DisplayName("Param diulang (?status=A&status=B) juga diterima sebagai daftar")
    void repeatedParamsAlsoAccepted() throws Exception {
        when(orderService.getOrderList(anyLong(), anyInt(), anyInt(), any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any())).thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 10), 0));

        mockMvc.perform(get("/order")
                        .header("Authorization", "Bearer token")
                        .param("workspaceId", "1")
                        .param("status", "PAID", "UNPAID"))
                .andExpect(status().isOk());

        ArgumentCaptor<OrderListFilter> captor = ArgumentCaptor.forClass(OrderListFilter.class);
        verify(orderService).getOrderList(anyLong(), anyInt(), anyInt(), captor.capture(), any(), any(), any(),
                any(), any(), any(), any(), any(), any());
        assertEquals(Set.of("PAID", "UNPAID"), captor.getValue().status());
    }
}
