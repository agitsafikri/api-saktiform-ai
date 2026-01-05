package com.saktiform.api.model.whatsapp.envelope;

import lombok.Data;
import java.util.List;

@Data
public class MessageAckPayload {
    private String chat_id;
    private String from;
    private List<String> ids;
    private String receipt_type;
    private String receipt_type_description;
    private String sender_id;
}
