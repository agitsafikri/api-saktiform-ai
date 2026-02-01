package com.saktiform.api.model.whatsapp.envelopev2;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class MessageDeletedPayload implements WebhookPayload {

    private String deletedMessageId;
    private String timestamp;
    private String from;
    private String chatId;

    private String originalContent;
    private String originalSender;
    private String originalTimestamp;
    private Boolean wasFromMe;

    private String originalMediaType;
    private String originalFilename;
}

