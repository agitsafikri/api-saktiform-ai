package com.saktiform.api.controller;

import com.saktiform.api.model.RestResponse;
import com.saktiform.api.model.whatsapp.RegisterWhatsappDto;
import com.saktiform.api.model.whatsapp.envelope.WebhookEnvelope;
import com.saktiform.api.service.WhatsappInstanceService;
import com.saktiform.api.service.chat.WhatsappService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/whatsapp")
public class WhatsappController {
    private final WhatsappInstanceService whatsappInstanceService;
    private final WhatsappService whatsappService;
    public WhatsappController(WhatsappInstanceService whatsappInstanceService, WhatsappService whatsappService) {
        this.whatsappInstanceService = whatsappInstanceService;
        this.whatsappService = whatsappService;
    }


    @PostMapping("/{port}/webhook")
    public ResponseEntity<String> receiveWebhook(@RequestBody WebhookEnvelope webhook,
                                                 @PathVariable String port) {

        System.out.println("==============================================");
        System.out.println("Webhook received from Whatsapp Webhook: "+ port);
        System.out.println(webhook);
        System.out.println("=============================================");
        whatsappService.processWebhook(port, webhook);
        return ResponseEntity.ok("Webhook received");
    }



    @PostMapping("")
    public ResponseEntity<?> registerWhatsapp(@RequestBody RegisterWhatsappDto data){
        RestResponse restResponse = new RestResponse();
        try{
            restResponse.setSuccess(true);
            restResponse.setMessage("success");
            whatsappInstanceService.registerWhatsapp(data);
            return ResponseEntity.ok(restResponse);
        }catch (Exception e){
            restResponse.setSuccess(false);
            restResponse.setMessage(e.getMessage());
            restResponse.setData(null);
            return ResponseEntity.badRequest().body(restResponse);
        }
    }

    @GetMapping("/connect")
    public ResponseEntity<?> connectWhatsapp(@RequestParam UUID wabaId){
        RestResponse restResponse = new RestResponse();
        try{
            restResponse.setData(whatsappInstanceService.connect(wabaId));
            restResponse.setSuccess(true);
            restResponse.setMessage("success");
            return ResponseEntity.ok(restResponse);
        }catch (Exception e){
            restResponse.setSuccess(false);
            restResponse.setMessage(e.getMessage());
            restResponse.setData(null);
            return ResponseEntity.badRequest().body(restResponse);
        }
    }


    @GetMapping("")
    public ResponseEntity<?> getListWhatsapp(@RequestParam(defaultValue = "1") Integer page,
                                             @RequestParam(defaultValue = "10") Integer limit){
        RestResponse restResponse = new RestResponse();
        try{
            restResponse.setSuccess(true);
            restResponse.setMessage("success");
            restResponse.setData(whatsappInstanceService.getListWhatsapp(page, limit));
            return ResponseEntity.ok(restResponse);
        }catch (Exception e){
            restResponse.setSuccess(false);
            restResponse.setMessage(e.getMessage());
            restResponse.setData(null);
            return ResponseEntity.badRequest().body(restResponse);
        }
    }


}
