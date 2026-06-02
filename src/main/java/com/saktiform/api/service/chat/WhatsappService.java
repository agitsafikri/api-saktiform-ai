package com.saktiform.api.service.chat;


import com.saktiform.api.model.whatsapp.envelopev2.*;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class WhatsappService {

    private final WhatsappMessageHandler whatsappMessageHandler;

    public WhatsappService(WhatsappMessageHandler whatsappMessageHandler) {

        this.whatsappMessageHandler = whatsappMessageHandler;

    }



    @Async
    public void processWebhook2(WebhookEnvelopeV2 webhook) {
        try {
            if(webhook.getPayload() instanceof MessagePayload payload){
                whatsappMessageHandler.handleMessagePayload(webhook);
            } else if (webhook.getPayload() instanceof MessageEditedPayload payload) {
                whatsappMessageHandler.handleMessageEdited(webhook);
            } else if (webhook.getPayload() instanceof MessageDeletedPayload payload) {
            }else if(webhook.getPayload() instanceof ReactionPayload payload){
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }



}
