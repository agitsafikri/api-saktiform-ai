package com.saktiform.api.service.chat.bot;

import com.saktiform.api.model.chat.ChatType;
import com.saktiform.api.model.chat.SendMessageDto;
import com.saktiform.api.model.chat.bot.ChatContext;
import com.saktiform.api.model.chat.bot.OrderChatInfo;
import com.saktiform.api.service.AppConfigService;
import com.saktiform.api.service.chat.ChatService;
import com.saktiform.api.service.order.OrderOrchestrationService;
import com.saktiform.api.service.order.OrderService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class BotService {

    private final ChatService chatService;
    private final ContextBuilderService contextBuilderService;
    private final OpenAiLlmService openAiLlmService;
    private final OrderOrchestrationService orderOrchestrationService;
    private final AppConfigService appConfigService;
//    private final GeminiLlmService geminiLlmService;
//    private final QdrantVectorService qdrantVectorService;

    private static final Pattern ORDER_PATTERN = Pattern.compile(
            "Halo, saya sudah melakukan pemesanan (.*?), atas nama (.*?)\\. Mohon segera diproses ya .*"
    );

    public BotService(ChatService chatService,
            ContextBuilderService contextBuilderService,
            OrderOrchestrationService orderOrchestrationService,
            AppConfigService appConfigService,
            OpenAiLlmService openAiLlmService) {
        this.chatService = chatService;
        this.orderOrchestrationService = orderOrchestrationService;
        this.contextBuilderService = contextBuilderService;
        this.openAiLlmService = openAiLlmService;
        this.appConfigService = appConfigService;
    }

    public void handleBotReply(UUID conversationId) {

        ChatContext context = contextBuilderService.build(conversationId);

        if (context.getOrderInfo() == null) {
            String reply = "Baik kak kami bantu, nanti admin kami followup ya 🙏";
            chatService.decreaseBotQuota(conversationId);

            var sendMessage = new SendMessageDto();
            sendMessage.setMessageType(ChatType.TEXT);
            sendMessage.setMessage(reply);
            sendMessage.setConversationId(conversationId);
            chatService.messageHandler(sendMessage, "BOT");

            return;
        }

        // 1. Coba Rule Based
        String reply =  ragBasedReply(context);


        if (reply == null || reply.contains("NULL") || reply.contains("null") || reply.isBlank() ) {
            reply = "Baik kak kami bantu, nanti admin kami followup ya 🙏";
            chatService.escelateToAdmin(conversationId);
        }

        chatService.decreaseBotQuota(conversationId);
        var sendMessage = new SendMessageDto();
        sendMessage.setMessageType(ChatType.TEXT);
        sendMessage.setMessage(reply);
        sendMessage.setConversationId(conversationId);
        chatService.messageHandler(sendMessage, "BOT");

    }


    private String ragBasedReply(ChatContext context) {
        try {

            String systemPrompt = appConfigService.getConfig("ai.system.prompt");
            String guardRailPrompt = appConfigService.getConfig("ai.guardrail.prompt");

            var guardRailRes = openAiLlmService.guardrailCheck(guardRailPrompt, context.getUserMessage());

            if (guardRailRes.equalsIgnoreCase("ALLOW")) {
                return openAiLlmService.generateReply(systemPrompt, context);
            }
            return null;

        } catch (Exception e) {
            return null;
        }
    }

    public  Boolean isOrderMessage(String message) {
        return ORDER_PATTERN.matcher(message).matches();
    }

    @Transactional
    public void sendConfirmationMessage(String message, UUID conversationId) {

        var orderInfo = extractOrderInfo(message);
        if (orderInfo == null) {
            String reply = "Baik kak, pesanan kakak akan saya proses ya. Mohon tunggu, nanti admin akan menghubungi kakak.";
        }else{
           var order = orderOrchestrationService.orderConfirmation(orderInfo, conversationId);
           if (order == null) {
               String reply = "Baik kak, pesanan kakak akan saya proses ya. Mohon tunggu, nanti admin akan menghubungi kakak.";

               var sendMessage = new SendMessageDto();
               sendMessage.setMessageType(ChatType.TEXT);
               sendMessage.setMessage(reply);
               sendMessage.setConversationId(conversationId);
               chatService.messageHandler(sendMessage, "BOT");
               chatService.escelateToAdmin(conversationId);
           }

           chatService.decreaseBotQuota(conversationId);

        }



    }

    public OrderChatInfo extractOrderInfo(String message) {

        Matcher matcher = ORDER_PATTERN.matcher(message);

        if (matcher.matches()) {
            String productName = matcher.group(1).trim();
            String customerName = matcher.group(2).trim();

            return new OrderChatInfo(productName, customerName);
        }

        return null; // tidak sesuai pattern
    }

}
