package com.saktiform.api.service.chat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.saktiform.api.entity.Chat;
import com.saktiform.api.entity.Contact;
import com.saktiform.api.entity.Conversation;
import com.saktiform.api.model.ConversationStatus;
import com.saktiform.api.model.chat.ChatListDto;
import com.saktiform.api.model.chat.ChatType;
import com.saktiform.api.model.chat.ConversationUpdatedData;
import com.saktiform.api.model.chat.bot.IncomingChatEvent;
import com.saktiform.api.model.event.ChatAsyncEvent;
import com.saktiform.api.model.whatsapp.MediaResult;
import com.saktiform.api.model.whatsapp.envelope.MessageAckPayload;
import com.saktiform.api.model.whatsapp.envelope.WebhookEnvelope;
import com.saktiform.api.service.WhatsappBusinessService;
import com.saktiform.api.service.WorkspaceService;
import com.saktiform.api.service.chat.bot.BotOrchestratorService;
import com.saktiform.api.util.MediaHelper;
import com.saktiform.api.util.PhoneNumberUtil;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Service
public class WhatsappService {

    private final WhatsappMessageHandler whatsappMessageHandler;

    public WhatsappService(WhatsappMessageHandler whatsappMessageHandler) {
        this.whatsappMessageHandler = whatsappMessageHandler;

    }

    @Async
    public void processWebhook(String port, WebhookEnvelope webhook) {
        try {
            if ("message.ack".equalsIgnoreCase(webhook.getEvent())) {
                whatsappMessageHandler.handleMessageAck(webhook.getPayload(), port);
            } else if ("message_revoked".equalsIgnoreCase(webhook.getAction())) {
                whatsappMessageHandler.handleMessageRevoked(webhook);
            } else if ("message_edited".equalsIgnoreCase(webhook.getAction())) {
                whatsappMessageHandler.handleMessageEdited(webhook);
            } else if (webhook.getMessage() != null) {
                whatsappMessageHandler.handleGenericMessage(webhook, port);

            } else {
                System.out.println("Unhandled webhook type");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


}
