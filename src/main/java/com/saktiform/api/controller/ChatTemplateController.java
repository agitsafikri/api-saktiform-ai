package com.saktiform.api.controller;

import com.saktiform.api.model.RestResponse;
import com.saktiform.api.model.template.AddChatTemplateDto;
import com.saktiform.api.service.MessageTemplateService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/template")
public class ChatTemplateController {
    MessageTemplateService messageTemplateService;

    public ChatTemplateController(MessageTemplateService messageTemplateService) {
        this.messageTemplateService = messageTemplateService;
    }

    @GetMapping("/variable")
    public ResponseEntity<?> getListVariabel() {
        RestResponse rest = new RestResponse();
        try{
            var data = messageTemplateService.getTemplateVariables();
            rest.setSuccess(true);
            rest.setData(data);
            rest.setMessage("Success");
            return ResponseEntity.ok(rest);
        }catch (Exception e){
            rest.setSuccess(false);
            rest.setMessage(e.getMessage());
            rest.setData(null);
            return ResponseEntity.badRequest().body(rest);
        }
    }

    @GetMapping
    public ResponseEntity<?> getTemplate(@RequestParam Long workspaceId, @RequestParam(defaultValue = "1") Integer page, @RequestParam(defaultValue = "10") Integer limit){
        RestResponse rest = new RestResponse();
        try {
            var data = messageTemplateService.getListTemplate(workspaceId, limit, page);
            rest.setSuccess(true);
            rest.setMessage("Success");
            rest.setData(data);
            return ResponseEntity.ok(rest);
        }catch (Exception e){
            rest.setSuccess(false);
            rest.setMessage(e.getMessage());
            rest.setData(null);
            return ResponseEntity.badRequest().body(rest);
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getDetailTemplate(@PathVariable UUID id){
        RestResponse rest = new RestResponse();
        try {
            var data = messageTemplateService.getDetail(id);
            rest.setSuccess(true);
            rest.setMessage("Success");
            rest.setData(data);
            return ResponseEntity.ok(rest);
        }catch (Exception e){
            rest.setSuccess(false);
            rest.setMessage(e.getMessage());
            rest.setData(null);
            return ResponseEntity.badRequest().body(rest);
        }
    }

    @PostMapping
    public ResponseEntity<?> saveTemplate(@RequestBody AddChatTemplateDto request){
        RestResponse rest = new RestResponse();
        try {
            messageTemplateService.upsertMessageTemplate(request);
            rest.setSuccess(true);
            rest.setMessage("Success");
            rest.setData(null);
            return ResponseEntity.ok(rest);
        }catch (Exception e){
            rest.setSuccess(false);
            rest.setMessage(e.getMessage());
            rest.setData(null);
            return ResponseEntity.badRequest().body(rest);
        }
    }

    @GetMapping("/delete")
    public ResponseEntity<?> deleteTemplate(@RequestParam UUID id){
        RestResponse rest = new RestResponse();
        try {
            messageTemplateService.deleteMessageTemplateById(id);
            rest.setSuccess(true);
            rest.setMessage("Success");
            rest.setData(null);
            return ResponseEntity.ok(rest);
        }catch (Exception e){
            rest.setSuccess(false);
            rest.setMessage(e.getMessage());
            rest.setData(null);
            return ResponseEntity.badRequest().body(rest);
        }
    }
}
