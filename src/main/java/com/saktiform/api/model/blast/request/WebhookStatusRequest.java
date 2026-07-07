package com.saktiform.api.model.blast.request;

import lombok.Getter;
import lombok.Setter;

/**
 * Payload webhook delivery dari provider WA (bentuk generik; dipetakan via adapter, OQ-7).
 */
@Getter
@Setter
public class WebhookStatusRequest {
    private String message_id;
    private String status;      // sent | delivered | read
    private Long timestamp;     // epoch seconds (opsional)
}
