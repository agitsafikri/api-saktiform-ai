package com.saktiform.api.service.chat;

import com.saktiform.api.model.whatsapp.envelope.WebhookEnvelope;
import com.saktiform.api.model.whatsapp.envelopev2.*;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class WhatsappService {

    private final WhatsappMessageHandler whatsappMessageHandler;
    private final WhatsappMessageHandler2 whatsappMessageHandler2;

    public WhatsappService(WhatsappMessageHandler whatsappMessageHandler, WhatsappMessageHandler2 whatsappMessageHandler2) {
        this.whatsappMessageHandler = whatsappMessageHandler;
        this.whatsappMessageHandler2 = whatsappMessageHandler2;

    }

    @Async
    public void processWebhook(String port, WebhookEnvelope webhook) {
        try {
            if ("message.ack".equalsIgnoreCase(webhook.getEvent())) {
                whatsappMessageHandler.handleMessageAck(webhook.getPayload(), port);
            } else if ("message_revoked".equalsIgnoreCase(webhook.getAction())) {
                whatsappMessageHandler.handleMessageRevoked(webhook);
            } else if ("message_edited".equalsIgnoreCase(webhook.getAction())) {
                whatsappMessageHandler.handleMessageEdited(webhook);
            } else if (webhook.getMessage() != null) {
                whatsappMessageHandler.handleGenericMessage(webhook, port);

            } else {
                System.out.println("Unhandled webhook type");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Async
    public void processWebhook2(WebhookEnvelopeV2 webhook) {
        try {
            if(webhook.getPayload() instanceof MessagePayload payload){
                System.out.println("webhook Message: "+webhook);
                whatsappMessageHandler2.handleMessagePayload(webhook);
            } else if (webhook.getPayload() instanceof MessageEditedPayload payload) {
                System.out.println("webhook Message Edited: "+webhook);
                whatsappMessageHandler2.handleMessageEdited(webhook);
            } else if (webhook.getPayload() instanceof MessageDeletedPayload payload) {
                System.out.println("webhook Message Deleted: "+webhook);
            }else if(webhook.getPayload() instanceof ReactionPayload payload){
                System.out.println("webhook Message reaction: "+webhook);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }



}
