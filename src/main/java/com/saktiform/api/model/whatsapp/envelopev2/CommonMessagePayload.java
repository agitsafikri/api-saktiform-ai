package com.saktiform.api.model.whatsapp.envelopev2;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public abstract class CommonMessagePayload implements WebhookPayload {

    private String id;
    private String chatId;
    private String from;
    private String fromLid;
    private String fromName;
    private String timestamp;
}
