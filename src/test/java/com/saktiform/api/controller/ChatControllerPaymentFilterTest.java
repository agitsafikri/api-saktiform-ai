package com.saktiform.api.controller;

import com.saktiform.api.configuration.JwtManager;
import com.saktiform.api.model.chat.ConversationListItemDto;
import com.saktiform.api.service.chat.ChatService;
import com.saktiform.api.service.chat.ConversationService;
import com.saktiform.api.service.chat.bot.BotOrchestratorService;
import com.saktiform.api.service.chat.bot.BotService;
import com.saktiform.api.service.order.OrderOrchestrationService;
import com.saktiform.api.service.order.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Filter {@code jenisPembayaran} pada {@code GET /conversation/assigned|unassigned}:
 * kosong = tanpa filter, enum tidak peka huruf, nilai tidak dikenal = 400 RestResponse.
 */
class ChatControllerPaymentFilterTest {

    private ConversationService conversationService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        conversationService = mock(ConversationService.class);
        Page<ConversationListItemDto> emptyPage = new PageImpl<>(List.of(), PageRequest.of(0, 10), 0);
        when(conversationService.getAssignedChat(any(), any(), any(), any(), any(), any(), any(), any(), any(),
                any(), any())).thenReturn(emptyPage);
        when(conversationService.getUnassignedChat(any(), any(), any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any())).thenReturn(emptyPage);

        mockMvc = MockMvcBuilders.standaloneSetup(new ChatController(conversationService, mock(ChatService.class),
                mock(JwtManager.class), mock(OrderOrchestrationService.class), mock(OrderService.class),
                mock(BotService.class), mock(BotOrchestratorService.class))).build();
    }

    private static MockHttpServletRequestBuilder request(String path, String jenisPembayaran) {
        var builder = get(path).param("workspaceId", "1");
        return jenisPembayaran == null ? builder : builder.param("jenisPembayaran", jenisPembayaran);
    }

    private void verifyPassed(String path, String expected) {
        if (path.endsWith("/assigned")) {
            verify(conversationService).getAssignedChat(any(), any(), any(), any(), any(), any(), any(), any(),
                    any(), any(), eq(expected));
        } else {
            verify(conversationService).getUnassignedChat(any(), any(), any(), any(), any(), any(), any(), any(),
                    any(), any(), any(), eq(expected));
        }
    }

    @ParameterizedTest(name = "{0} jenisPembayaran=''{1}'' -> {2}")
    @CsvSource(nullValues = "NULL", value = {
            "/conversation/assigned,   NULL,          NULL",
            "/conversation/assigned,   '',            NULL",
            "/conversation/assigned,   cod,           COD",
            "/conversation/unassigned, '',            NULL",
            "/conversation/unassigned, bank_transfer, BANK_TRANSFER",
    })
    @DisplayName("Nilai kosong/valid dinormalisasi sebelum diteruskan ke service")
    void normalized(String path, String raw, String expected) throws Exception {
        mockMvc.perform(request(path, raw))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
        verifyPassed(path, expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/conversation/assigned", "/conversation/unassigned"})
    @DisplayName("Nilai tidak dikenal: 400 RestResponse + ErrorDto, service tidak dipanggil")
    void rejected(String path) throws Exception {
        mockMvc.perform(request(path, "Bank Transfer"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data[0].field").value("jenisPembayaran"))
                .andExpect(jsonPath("$.data[0].code").value("INVALID_FILTER_VALUE"));
        verify(conversationService, never()).getAssignedChat(any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any());
        verify(conversationService, never()).getUnassignedChat(any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any());
    }
}
