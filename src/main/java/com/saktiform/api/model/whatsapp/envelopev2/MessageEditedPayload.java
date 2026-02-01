package com.saktiform.api.model.whatsapp.envelopev2;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class MessageEditedPayload implements WebhookPayload {

    /**
     * ID event edit (bukan ID pesan asli)
     */
    private String id;

    private String chatId;
    private String from;
    private String fromName;
    private String timestamp;

    /**
     * ID pesan asli yang diedit
     */
    private String originalMessageId;

    /**
     * Text terbaru
     */
    private String body;
}

