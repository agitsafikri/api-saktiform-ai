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
public class SendMessageDto {
    UUID conversationId;
    ChatType messageType;
    String mediaLink;
    String message;
    UUID repliedMessageId;
}
