package com.saktiform.api.controller;

import com.saktiform.api.configuration.JwtManager;
import com.saktiform.api.model.RestResponse;
import com.saktiform.api.model.chat.ConversationSelectOrder;
import com.saktiform.api.model.chat.QuickChatRequest;
import com.saktiform.api.model.chat.SendMessageDto;
import com.saktiform.api.service.chat.ConversationService;
import com.saktiform.api.service.chat.ChatService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("")
public class ChatController {
    ConversationService conversationService;
    private final ChatService chatService;
    private final JwtManager jwtManager;

    public ChatController(ConversationService conversationService, ChatService chatService, JwtManager jwtManager) {
        this.conversationService = conversationService;
        this.chatService = chatService;
        this.jwtManager = jwtManager;

    }

    @GetMapping("/conversation/assigned")
    public ResponseEntity<?> getAssignedChat(@RequestParam Long workspaceId, @RequestParam(defaultValue = "1") Integer page, @RequestParam(defaultValue = "10") Integer limit) {
        RestResponse rest = new RestResponse();
        try{
            var data = conversationService.getAssignedChat(workspaceId, page, limit);
            rest.setSuccess(true);
            rest.setMessage("Success");
            rest.setData(data);
            return ResponseEntity.ok(rest);
        }catch (Exception e){
            e.printStackTrace();
            rest.setSuccess(false);
            rest.setMessage(e.getMessage());
            rest.setData(null);
            return ResponseEntity.badRequest().body(rest);
        }
    }

    @GetMapping("/conversation/unassigned")
    public ResponseEntity<?> getUnassignedChat(@RequestParam Long workspaceId, @RequestParam(defaultValue = "1") Integer page, @RequestParam(defaultValue = "10") Integer limit) {
        RestResponse rest = new RestResponse();
        try{
            var data = conversationService.getUnassignedChat(workspaceId, page, limit);
            rest.setSuccess(true);
            rest.setMessage("Success");
            rest.setData(data);
            return ResponseEntity.ok(rest);
        }catch (Exception e){
            e.printStackTrace();
            rest.setSuccess(false);
            rest.setMessage(e.getMessage());
            rest.setData(null);
            return ResponseEntity.badRequest().body(rest);
        }
    }

//    @GetMapping("/conversation/archived")
//    public String getArchivedConversation(@RequestParam(defaultValue = "1") Integer page,@RequestParam(defaultValue = "10") Integer limit) {
//        return "test1";
//    }
//
    @GetMapping("/conversation/detail")
    public ResponseEntity<?> getDetailConversation(@RequestParam UUID conversationId) {
        RestResponse rest = new RestResponse();
        try{
            var data = conversationService.getConversationDetail(conversationId);
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

    @GetMapping("/conversation/order")
    public ResponseEntity<?> getConversationOrder(@RequestParam UUID conversationId) {
        RestResponse rest = new RestResponse();
        try{
            var data = conversationService.getConversationOrder(conversationId);
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

    @GetMapping("/conversation/message")
    public ResponseEntity<?> getListChat(@RequestParam UUID conversationId, @RequestParam(defaultValue = "1") Integer page, @RequestParam(defaultValue = "10") Integer limit) {
        RestResponse rest = new RestResponse();
        try{
            var data = conversationService.getListMessage(conversationId, page, limit);
            rest.setSuccess(true);
            rest.setMessage("Success");
            rest.setData(data);
            return ResponseEntity.ok(rest);
        }catch (Exception e){
            e.printStackTrace();
            rest.setSuccess(false);
            rest.setMessage(e.getMessage());
            rest.setData(null);
            return ResponseEntity.badRequest().body(rest);
        }
    }

    @PostMapping("/send-message")
    public ResponseEntity<?> sendMessage(@RequestBody SendMessageDto data,  HttpServletRequest request) {
        RestResponse rest = new RestResponse();
        try{
            String username = jwtManager.getUsernameByToken(request.getHeader("Authorization").substring(7));
            chatService.messageHandler(data, username);
            rest.setSuccess(true);
            rest.setMessage("Success");
            rest.setData(null);
            return ResponseEntity.ok(rest);
        }catch (Exception e){
            e.printStackTrace();
            rest.setSuccess(false);
            rest.setMessage(e.getMessage());
            rest.setData(null);
            return ResponseEntity.badRequest().body(rest);
        }
    }

    @GetMapping("/conversation/takeover")
    public ResponseEntity<?> sendMessage(@RequestParam UUID conversationId, HttpServletRequest request) {
        RestResponse rest = new RestResponse();
        try{
            String username = jwtManager.getUsernameByToken(request.getHeader("Authorization").substring(7));
            chatService.takeoverConversation(conversationId, username);
            rest.setSuccess(true);
            rest.setMessage("Success");
            rest.setData(null);
            return ResponseEntity.ok(rest);
        }catch (Exception e){
            e.printStackTrace();
            rest.setSuccess(false);
            rest.setMessage(e.getMessage());
            rest.setData(null);
            return ResponseEntity.badRequest().body(rest);
        }
    }

    @PostMapping("/conversation/select-order")
    public ResponseEntity<?> conversationSelectOrder(@RequestBody ConversationSelectOrder selectOrder) {
        RestResponse rest = new RestResponse();
        try{
            conversationService.selectConversationOrder(selectOrder);
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

    @PostMapping("conversation/quick-chat")
    public ResponseEntity<?> getQuickChat(@RequestBody QuickChatRequest quickChatRequest) {
        RestResponse rest = new RestResponse();
        try{
           var data =  conversationService.getQuickChat(quickChatRequest);
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


}
