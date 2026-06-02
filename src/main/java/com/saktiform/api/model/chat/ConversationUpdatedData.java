package com.saktiform.api.model.chat;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ConversationUpdatedData {
    UUID id;
    String contactName;
    String lastMessage;
    String lastMessageType;
    String lastMessageTime;
    String status;
    Integer unreadMessageCount;
    String chatStatus;
}
