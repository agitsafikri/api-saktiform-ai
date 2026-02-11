package com.saktiform.api.service.chat;

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
import com.saktiform.api.model.whatsapp.envelopev2.MediaContent;
import com.saktiform.api.model.whatsapp.envelopev2.MessageEditedPayload;
import com.saktiform.api.model.whatsapp.envelopev2.MessagePayload;
import com.saktiform.api.model.whatsapp.envelopev2.WebhookEnvelopeV2;
import com.saktiform.api.service.StorageService;
import com.saktiform.api.service.WhatsappBusinessService;
import com.saktiform.api.service.WorkspaceService;
import com.saktiform.api.util.MediaHelper;
import com.saktiform.api.util.PhoneNumberUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Service
public class WhatsappMessageHandler2 {
    private final ApplicationEventPublisher eventPublisher;
    private final ConversationService conversationService;
    private final ChatMessageService chatMessageService;
    private final WhatsappBusinessService whatsappBusinessService;
    private final WorkspaceService workspaceService;
    private final MediaHelper mediaHelper;
    private final StorageService storageService;
    private static DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    @Value("${whatsapp.multidevice.api.url}")
    private String whatsappImageUrl;

//    @Value("${saktiform.api.url}")
//    private String apiUrl;



    public WhatsappMessageHandler2(ApplicationEventPublisher eventPublisher, ConversationService conversationService,
                                   ChatMessageService chatMessageService, WhatsappBusinessService whatsappBusinessService,
                                   WorkspaceService workspaceService, MediaHelper mediaHelper, StorageService storageService) {
        this.eventPublisher = eventPublisher;
        this.conversationService = conversationService;
        this.chatMessageService = chatMessageService;
        this.whatsappBusinessService = whatsappBusinessService;
        this.workspaceService = workspaceService;
        this.mediaHelper = mediaHelper;
        this.storageService = storageService;
    }

    @Transactional
    public void handleMessagePayload(WebhookEnvelopeV2 webhook){
        var waba = whatsappBusinessService.findByNomorWhatsapp(PhoneNumberUtil.extractPhoneNumber(webhook.getDeviceId()));
        var workspace = workspaceService.findByWabaId(waba.getId());

        if (workspace == null) {
            return;
        }
        var payload = (MessagePayload) webhook.getPayload();
        Boolean isNewConversation = false;
        var sender = PhoneNumberUtil.normalizeToIndonesianFormat(PhoneNumberUtil.extractPhoneNumber(payload.getFrom()));

        var contact = conversationService.findContactByPhoneNumberAndIdWorkspace(sender, workspace.getId());
        if (contact == null) {
            contact = new Contact();
            contact.setPhoneNumber(sender);
            contact.setIdWorkspace(workspace.getId());
            contact.setNamaKontak(payload.getFromName() != null ? payload.getFromName() : "Unknown");
            contact.setCreatedAt(Instant.now());

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
        conversation.setIsUnread(true);
        conversation.setUnreadMessageCount(conversation.getUnreadMessageCount() + 1);
        conversation.setUpdatedAt(Instant.now());
        conversation = conversationService.saveConversation(conversation);

        var newCustChat = new Chat();

        if (payload.getImage() != null){
            newCustChat = saveMedia(conversation.getId(), payload.getImage(), ChatType.IMAGE.name(), payload.getId());
        }else if (payload.getDocument() != null){
            newCustChat = saveMedia(conversation.getId(), payload.getDocument(), ChatType.DOCUMENT.name(), payload.getId());
        }else if (payload.getVideo() != null){
            newCustChat = saveMedia(conversation.getId(), payload.getVideo(), ChatType.VIDEO.name(), payload.getId());
        }else if (payload.getAudio() != null){
            newCustChat = saveMedia(conversation.getId(), payload.getAudio(), ChatType.AUDIO.name(), payload.getId());
        }else if (payload.getSticker() != null) {
            newCustChat = saveMedia(conversation.getId(), payload.getSticker(), ChatType.IMAGE.name(), payload.getId());
        }else if (payload.getVideoNote() != null) {
            newCustChat = saveMedia(conversation.getId(), payload.getVideoNote(), ChatType.VIDEO.name(), payload.getId());
        }else if (payload.getBody() != null) {
           newCustChat = saveTextMessage(conversation.getId(), payload);
        }

        Chat replyChat = null;
        if(payload.getRepliedToId() != null){
            replyChat = chatMessageService.findByWhatsappMessageId(payload.getRepliedToId());
            if(replyChat != null){
                newCustChat.setRepliedTo(replyChat);
            }
        }


        var newConversationUpdate = new ConversationUpdatedData();
        newConversationUpdate.setUnreadMessageCount(conversation.getUnreadMessageCount());
        newConversationUpdate.setContactName(contact.getNamaKontak());
        newConversationUpdate.setUnreadMessageCount(conversation.getUnreadMessageCount());
        newConversationUpdate.setId(conversation.getId());
        newConversationUpdate.setLastMessage(newCustChat.getPesan());
        newConversationUpdate.setLastMessageType(newCustChat.getType());
        newConversationUpdate.setLastMessageTime(
                newCustChat.getSentAt() != null ? newCustChat.getSentAt().atZone(ZoneId.of("Asia/Jakarta")).format(formatter) : null);
        newConversationUpdate.setStatus(conversation.getStatus());

        var newChatUpdate = new ChatListDto(newCustChat.getId()
                , newCustChat.getType()
                , newCustChat.getPengirim()
                , newCustChat.getPesan()
                , newCustChat.getMedia() != null ? storageService.getPublicUrl(newCustChat.getMedia()) : null
                , newCustChat.getSentAt());

        if(replyChat!=null){
            newChatUpdate.setRepliedMessage(new ChatListDto(
                    replyChat.getId(),
                    replyChat.getType(),
                    replyChat.getPengirim(),
                    replyChat.getPesan(),
                    replyChat.getMedia() != null ? storageService.getPublicUrl(newCustChat.getMedia()) : null,
                    replyChat.getSentAt()
            ));
        }

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

        eventPublisher.publishEvent(new IncomingChatEvent(newCustChat.getId()));
    }

    private Chat saveTextMessage(UUID idConversation, MessagePayload payload){
        Chat newCustChat = new Chat();
        newCustChat.setIdConversation(idConversation);
        newCustChat.setMessageId(payload.getId());
        newCustChat.setSentAt(Instant.now());
        newCustChat.setPesan(payload.getBody());
        newCustChat.setType(ChatType.TEXT.name());
        newCustChat.setPengirim("CUSTOMER");
        return chatMessageService.saveChat(newCustChat);
    }

    private Chat saveMedia(UUID idConversation, MediaContent mediaContent, String type, String chatId){
        Chat newCustChat = new Chat();
        var mediaUrl = whatsappImageUrl +"/"+ mediaContent.getPath();
        var mediaType = mediaContent.getMimeType();
        MediaResult mediaResult = mediaHelper.saveMediaFromUrl(mediaUrl, mediaType, chatId != null ? chatId : UUID.randomUUID().toString());
        newCustChat.setIdConversation(idConversation);
        newCustChat.setMessageId(chatId != null ? chatId : UUID.randomUUID().toString());
        newCustChat.setSentAt(Instant.now());
        newCustChat.setMedia(mediaResult.localPath());
        newCustChat.setPesan(mediaContent.getCaption());
        newCustChat.setPengirim("CUSTOMER");
        newCustChat.setType(type);

        return chatMessageService.saveChat(newCustChat);
    }

    @Transactional
    public void handleMessageEdited(WebhookEnvelopeV2 webhook) {
        var waba = whatsappBusinessService.findByNomorWhatsapp(PhoneNumberUtil.extractPhoneNumber(webhook.getDeviceId()));
        var workspace = workspaceService.findByWabaId(waba.getId());

        if (workspace == null) {
            return;
        }
        var payload = (MessageEditedPayload) webhook.getPayload();
        System.out.println("Message edited: " + payload.getBody());
    }
}
