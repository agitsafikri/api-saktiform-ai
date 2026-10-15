package com.saktiform.api.model.chat;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SendMessageDto {
    UUID conversationId;
    ChatType messageType;
    // Menerima string tunggal (client lama) maupun array URL
    @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
    List<String> mediaLink;
    String message;
    UUID repliedMessageId;
}
