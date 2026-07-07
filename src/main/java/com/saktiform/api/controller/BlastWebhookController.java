package com.saktiform.api.controller;

import com.saktiform.api.model.RestResponse;
import com.saktiform.api.model.blast.request.WebhookStatusRequest;
import com.saktiform.api.service.blast.BlastStatusService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

/**
 * Webhook delivery provider WA (public, diverifikasi shared-secret). Selalu balas 200 (ack) agar
 * provider tidak retry berlebihan; error internal di-log (OQ-7).
 */
@RestController
@RequestMapping("/blast/webhook")
public class BlastWebhookController {

    private static final Logger log = LoggerFactory.getLogger(BlastWebhookController.class);

    private final BlastStatusService statusService;

    @Value("${blast.webhook.secret:}")
    private String webhookSecret;

    public BlastWebhookController(BlastStatusService statusService) {
        this.statusService = statusService;
    }

    @PostMapping("/status")
    public ResponseEntity<?> status(@RequestBody WebhookStatusRequest body,
                                    @RequestHeader(value = "X-Blast-Secret", required = false) String secret) {
        // Verifikasi shared-secret bila dikonfigurasi
        if (webhookSecret != null && !webhookSecret.isBlank() && !webhookSecret.equals(secret)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new RestResponse(false, "Invalid webhook secret"));
        }
        try {
            Instant ts = body.getTimestamp() != null ? Instant.ofEpochSecond(body.getTimestamp()) : Instant.now();
            statusService.applyDeliveryStatus(body.getMessage_id(), body.getStatus(), ts);
        } catch (Exception e) {
            log.error("Blast webhook error: {}", e.getMessage());
        }
        return ResponseEntity.ok(new RestResponse(true, "ok")); // selalu ACK
    }
}
