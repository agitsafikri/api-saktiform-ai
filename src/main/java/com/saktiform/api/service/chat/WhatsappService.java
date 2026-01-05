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

    private final ApplicationEventPublisher eventPublisher;
    private final ConversationService conversationService;
    private final ChatMessageService chatMessageService;
    private final WhatsappBusinessService whatsappBusinessService;
    private final WorkspaceService workspaceService;
    private final MediaHelper mediaHelper;
    private final BotOrchestratorService botOrchestratorService;
    private static DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    @Value("${whatsapp.api.url}")
    private String whatsappApiUrl;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public WhatsappService(ApplicationEventPublisher eventPublisher, ConversationService conversationService,
            ChatMessageService chatMessageService, WhatsappBusinessService whatsappBusinessService,
            WorkspaceService workspaceService, MediaHelper mediaHelper, BotOrchestratorService botOrchestratorService) {

        this.eventPublisher = eventPublisher;
        this.conversationService = conversationService;
        this.chatMessageService = chatMessageService;
        this.whatsappBusinessService = whatsappBusinessService;
        this.workspaceService = workspaceService;
        this.mediaHelper = mediaHelper;
        this.botOrchestratorService = botOrchestratorService;
    }

    @Async
    public void processWebhook(String port, WebhookEnvelope webhook) {
        try {
            if ("message.ack".equalsIgnoreCase(webhook.getEvent())) {
                System.out.println("msg ack");
                handleMessageAck(webhook.getPayload(), port);
            } else if ("message_revoked".equalsIgnoreCase(webhook.getAction())) {
                System.out.println("msg revoked");
                handleMessageRevoked(webhook);
            } else if ("message_edited".equalsIgnoreCase(webhook.getAction())) {
                System.out.println("msg edited");
                handleMessageEdited(webhook);
            } else if (webhook.getMessage() != null) {
                handleGenericMessage(webhook, port);

            } else {
                System.out.println("Unhandled webhook type");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void handleMessageAck(JsonNode node, String waba) {
        try {
            MessageAckPayload ack = objectMapper.treeToValue(node, MessageAckPayload.class);
            System.out.printf("📨 Message %s → chat %s (%s)%n", ack.getReceipt_type(), ack.getChat_id(),
                    ack.getReceipt_type_description());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Transactional
    protected void handleGenericMessage(WebhookEnvelope webhook, String port) {
        Boolean isNewConversation = false;
        var sender = PhoneNumberUtil.normalizeToIndonesianFormat(PhoneNumberUtil.extractPhoneNumber(webhook.getFrom()));
        var waba = whatsappBusinessService.findByPort(Integer.parseInt(port));
        var workspace = workspaceService.findByWaba_id(waba.getId());
        if (workspace == null) {
            return;
        }

        if (waba.getNomorWhatsapp().equals(sender)) {
            return;
        }
        var contact = conversationService.findContactByPhoneNumberAndIdWorkspace(sender, workspace.getId());

        if (contact == null) {
            contact = new Contact();
            contact.setPhoneNumber(sender);
            contact.setIdWorkspace(workspace.getId());
            contact.setNamaKontak(webhook.getPushname() != null ? webhook.getPushname() : "Unknown");

            contact = conversationService.saveContact(contact);
        }

        var conversation = conversationService.findByIdContact(contact.getId());
        if (conversation == null) {
            isNewConversation = true;
            conversation = new Conversation();
            conversation.setStatus(ConversationStatus.UNASSIGNED.name());
            conversation.setIdContact(contact.getId());
            conversation.setCreatedAt(Instant.now());
            conversation.setHandleByBot(true); // Initialize
                                               // bot
                                               // handling
                                               // for
                                               // new
                                               // conversations
        }
        conversation.setUpdatedAt(Instant.now());
        conversation = conversationService.saveConversation(conversation);

        var chat = new Chat();
        JsonNode msg = webhook.getMessage();
        if (msg.has("text")) {
            chat.setIdConversation(conversation.getId());
            chat.setMessageId(msg.get("id").asText());
            chat.setSentAt(Instant.now());
            chat.setPesan(msg.get("text").asText());
            chat.setType(ChatType.TEXT.name());
            chat.setPengirim("CUSTOMER");
        }
        var mediaBaseUrl = whatsappApiUrl + ":" + waba.getPort() + "/";
        if (webhook.getImage() != null) {

            var mediaUrl = mediaBaseUrl + webhook.getImage().get("media_path").asText();
            var mediaType = webhook.getImage().get("mime_type").asText();
            MediaResult mediaResult = mediaHelper.saveMediaFromUrl(mediaUrl, mediaType, msg.get("id").asText());

            chat.setMedia(mediaResult.publicUrl());
            chat.setPesan(webhook.getImage().get("caption").asText());
            chat.setType(ChatType.IMAGE.name());
        }
        if (webhook.getVideo() != null) {
            var mediaUrl = mediaBaseUrl + webhook.getVideo().get("media_path").asText();
            var mediaType = webhook.getVideo().get("mime_type").asText();
            MediaResult mediaResult = mediaHelper.saveMediaFromUrl(mediaUrl, mediaType, msg.get("id").asText());

            chat.setMedia(mediaResult.publicUrl());
            chat.setPesan(webhook.getVideo().get("caption").asText());
            chat.setType(ChatType.VIDEO.name());
        }
        if (webhook.getDocument() != null) {
            var mediaUrl = mediaBaseUrl + webhook.getDocument().get("media_path").asText();
            var mediaType = webhook.getDocument().get("mime_type").asText();
            MediaResult mediaResult = mediaHelper.saveMediaFromUrl(mediaUrl, mediaType, msg.get("id").asText());

            chat.setMedia(mediaResult.publicUrl());
            chat.setPesan(webhook.getDocument().get("caption").asText());
            chat.setType(ChatType.DOCUMENT.name());
        }

        if (webhook.getAudio() != null) {
            var mediaUrl = mediaBaseUrl + webhook.getAudio().get("media_path").asText();
            var mediaType = webhook.getAudio().get("mime_type").asText();
            MediaResult mediaResult = mediaHelper.saveMediaFromUrl(mediaUrl, mediaType, msg.get("id").asText());

            chat.setMedia(mediaResult.publicUrl());
            chat.setPesan(webhook.getAudio().get("caption").asText());
            chat.setType(ChatType.AUDIO.name());
        }

        if (webhook.getSticker() != null) {
            var mediaUrl = mediaBaseUrl + webhook.getSticker().get("media_path").asText();
            var mediaType = webhook.getSticker().get("mime_type").asText();
            MediaResult mediaResult = mediaHelper.saveMediaFromUrl(mediaUrl, mediaType, msg.get("id").asText());

            chat.setMedia(mediaResult.publicUrl());
            chat.setPesan(webhook.getSticker().get("caption").asText());
            chat.setType(ChatType.IMAGE.name());
        }

        chatMessageService.saveChat(chat);

        var newConversationUpdate = new ConversationUpdatedData();
        newConversationUpdate.setContactName(contact.getNamaKontak());
        newConversationUpdate.setId(conversation.getId());
        newConversationUpdate.setLastMessage(chat.getPesan());
        newConversationUpdate.setLastMessageType(chat.getType());
        newConversationUpdate.setLastMessageTime(
                chat.getSentAt() != null ? chat.getSentAt().atZone(ZoneId.of("Asia/Jakarta")).format(formatter) : null);
        newConversationUpdate.setStatus(conversation.getStatus());

        var newChatUpdate = new ChatListDto(chat.getId(), chat.getType(), chat.getPengirim(), chat.getPesan(),
                chat.getMedia(), chat.getSentAt());

        eventPublisher.publishEvent(ChatAsyncEvent.builder().eventType(ChatAsyncEvent.EventType.NEW_MESSAGE)
                .conversationId(conversation.getId()).data(newChatUpdate).timestamp(newChatUpdate.getTanggal())
                .build());

        if (isNewConversation) {
            eventPublisher.publishEvent(ChatAsyncEvent.builder()
                    .eventType(ChatAsyncEvent.EventType.UNASSIGNED_CONVERSATION_CREATED).workspaceId(workspace.getId())
                    .data(newConversationUpdate).timestamp(newConversationUpdate.getLastMessageTime()).build());
        } else {
            if (conversation.getStatus().equals(ConversationStatus.UNASSIGNED.name())) {
                eventPublisher.publishEvent(
                        ChatAsyncEvent.builder().eventType(ChatAsyncEvent.EventType.UNASSIGNED_CONVERSATION_UPDATED)
                                .workspaceId(workspace.getId()).data(newConversationUpdate)
                                .timestamp(newConversationUpdate.getLastMessageTime()).build());
            } else {
                eventPublisher.publishEvent(
                        ChatAsyncEvent.builder().eventType(ChatAsyncEvent.EventType.ASSIGNED_CONVERSATION_UPDATED)
                                .workspaceId(workspace.getId()).data(newConversationUpdate)
                                .timestamp(newConversationUpdate.getLastMessageTime()).build());
            }
        }

        eventPublisher.publishEvent(new IncomingChatEvent(chat.getId()));
    }

    // 🗑️ Handle message revoked
    private void handleMessageRevoked(WebhookEnvelope webhook) {
        System.out.printf("Message revoked → chat: %s, messageId: %s%n", webhook.getChat_id(),
                webhook.getMessage().path("id").asText());
    }

    // ✏️ Handle message edited
    private void handleMessageEdited(WebhookEnvelope webhook) {
        System.out.printf("Message edited in chat : " + webhook);
    }
}
