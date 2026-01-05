package com.saktiform.api.model.whatsapp.envelope;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

@Data
public class WebhookEnvelope {
    private String event;
    private String action;
    private String timestamp;
    private String sender_id;
    private String chat_id;
    private String from;
    private String pushname;
    private String edited_text;

    private JsonNode message;
    private JsonNode payload;
    private JsonNode reaction;
    private JsonNode image;
    private JsonNode video;
    private JsonNode audio;
    private JsonNode document;
    private JsonNode sticker;
    private JsonNode contact;
    private JsonNode location;

    private Boolean view_once;
    private Boolean forwarded;
}
