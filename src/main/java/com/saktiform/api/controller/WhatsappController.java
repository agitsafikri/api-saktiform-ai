package com.saktiform.api.controller;

import com.saktiform.api.model.RestResponse;
import com.saktiform.api.model.whatsapp.ConnectRequest;
import com.saktiform.api.model.whatsapp.DeleteWhatsappPayload;
import com.saktiform.api.model.whatsapp.RegisterWhatsappDto;
import com.saktiform.api.model.whatsapp.envelope.WebhookEnvelope;
import com.saktiform.api.model.whatsapp.envelopev2.MessagePayload;
import com.saktiform.api.model.whatsapp.envelopev2.WebhookEnvelopeV2;
import com.saktiform.api.service.WhatsappInstanceService;
import com.saktiform.api.service.chat.WhatsappService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

@RestController
@RequestMapping("/whatsapp")
public class WhatsappController {
    private final WhatsappInstanceService whatsappInstanceService;
    private final WhatsappService whatsappService;
    public WhatsappController(WhatsappInstanceService whatsappInstanceService, WhatsappService whatsappService) {
        this.whatsappInstanceService = whatsappInstanceService;
        this.whatsappService = whatsappService;
    }


    @PostMapping("/webhook")
    public ResponseEntity<String> receiveWebhookV2(@RequestBody WebhookEnvelopeV2 webhook) {
        try{

            whatsappService.processWebhook2(webhook);
        }catch (Exception e){

        }



        return ResponseEntity.ok("Webhook received");
    }



    @PostMapping("")
    public ResponseEntity<?> registerWhatsapp(@RequestBody RegisterWhatsappDto data){
        RestResponse restResponse = new RestResponse();
        try{
            restResponse.setSuccess(true);
            restResponse.setMessage("success");
            whatsappInstanceService.registerWhatsappMultiDevice(data);
            return ResponseEntity.ok(restResponse);
        }catch (Exception e){
            restResponse.setSuccess(false);
            restResponse.setMessage(e.getMessage());
            restResponse.setData(null);
            return ResponseEntity.badRequest().body(restResponse);
        }
    }

    @PostMapping("/connect")
    public ResponseEntity<?> connectWhatsapp(@RequestBody ConnectRequest request){
        RestResponse restResponse = new RestResponse();
        try{
            restResponse.setData(whatsappInstanceService.connectMultiDevice(request.getWabaId()).getResults());
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
                                             @RequestParam(defaultValue = "10") Integer limit,
                                             @RequestParam(required = false, defaultValue = "")String search){
        RestResponse restResponse = new RestResponse();
        try{
            restResponse.setSuccess(true);
            restResponse.setMessage("success");
            restResponse.setData(whatsappInstanceService.getListWhatsapp(page, limit, search));
            return ResponseEntity.ok(restResponse);
        }catch (Exception e){
            restResponse.setSuccess(false);
            restResponse.setMessage(e.getMessage());
            restResponse.setData(null);
            return ResponseEntity.badRequest().body(restResponse);
        }
    }

    @GetMapping("/available")
    public ResponseEntity<?> getListWhatsapp(){
        RestResponse restResponse = new RestResponse();
        try{
            restResponse.setSuccess(true);
            restResponse.setMessage("success");
            restResponse.setData(whatsappInstanceService.getAvailableWhatsapp());
            return ResponseEntity.ok(restResponse);
        }catch (Exception e){
            restResponse.setSuccess(false);
            restResponse.setMessage(e.getMessage());
            restResponse.setData(null);
            return ResponseEntity.badRequest().body(restResponse);
        }
    }

    @PostMapping("/delete")
    public ResponseEntity<?> deleteWhatsapp(@RequestBody DeleteWhatsappPayload data){
        RestResponse restResponse = new RestResponse();
        try{
            restResponse.setSuccess(true);
            restResponse.setMessage("success");
            whatsappInstanceService.deleteWhatsapp(data.getId());
            return ResponseEntity.ok(restResponse);
        }catch (Exception e){
            restResponse.setSuccess(false);
            restResponse.setMessage(e.getMessage());
            restResponse.setData(null);
            return ResponseEntity.badRequest().body(restResponse);
        }
    }


}
