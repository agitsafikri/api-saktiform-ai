package com.saktiform.api.model.chat;

import com.saktiform.api.model.label.response.LabelDto;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.UUID;

/**
 * Item list conversation (pembungkus projection {@link ConversationDto} + label terpasang).
 * Bentuk JSON adalah superset dari {@link ConversationDto} (field lama identik + tambahan
 * {@code labels}) sehingga backward compatible.
 */
@Getter
@Setter
public class ConversationListItemDto {

    private UUID id;
    private String contactName;
    private String lastMessage;
    private String lastMessageType;
    private String lastMessageTime;   // sudah terformat Asia/Jakarta oleh projection
    private String status;
    private String chatStatus;
    private Integer unreadMessageCount;
    private List<LabelDto> labels;

    public ConversationListItemDto(ConversationDto p, List<LabelDto> labels) {
        this.id = p.getId();
        this.contactName = p.getContactName();
        this.lastMessage = p.getLastMessage();
        this.lastMessageType = p.getLastMessageType();
        this.lastMessageTime = p.getLastMessageTime();
        this.status = p.getStatus();
        this.chatStatus = p.getChatStatus();
        this.unreadMessageCount = p.getUnreadMessageCount();
        this.labels = labels == null ? List.of() : labels;
    }
}
