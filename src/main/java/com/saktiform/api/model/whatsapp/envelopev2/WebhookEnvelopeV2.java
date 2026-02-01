package com.saktiform.api.model.whatsapp.envelopev2;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class WebhookEnvelopeV2 {

    /**
     * message, message.ack, message.reaction,
     * message.revoked, message.edited,
     * message.deleted, group.participants
     */
    private String event;

    /**
     * Device JID
     * contoh: 628123456789@s.whatsapp.net
     */
    private String deviceId;

    /**
     * RFC3339 timestamp (optional, not all events have it)
     */
    private String timestamp;

    /**
     * Event-specific payload
     */
    @JsonTypeInfo(
            use = JsonTypeInfo.Id.NAME,
            include = JsonTypeInfo.As.EXTERNAL_PROPERTY,
            property = "event"
    )
    @JsonSubTypes({
            @JsonSubTypes.Type(value = MessagePayload.class, name = "message"),
            @JsonSubTypes.Type(value = MessageEditedPayload.class, name = "message.edited"),
            @JsonSubTypes.Type(value = MessageDeletedPayload.class, name = "message.deleted"),
            @JsonSubTypes.Type(value = ReactionPayload.class, name = "message.reaction")
    })
   private WebhookPayload payload;
}

