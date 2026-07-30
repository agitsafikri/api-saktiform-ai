package com.saktiform.api.controller;

import com.saktiform.api.configuration.JwtManager;
import com.saktiform.api.model.chat.*;
import com.saktiform.api.model.RestResponse;
import com.saktiform.api.model.chat.bot.IncomingChatEvent;
import com.saktiform.api.service.chat.bot.BotOrchestratorService;
import com.saktiform.api.service.chat.bot.BotService;
import com.saktiform.api.service.order.OrderOrchestrationService;
import com.saktiform.api.service.order.OrderService;
import com.saktiform.api.service.chat.ConversationService;
import com.saktiform.api.service.chat.ChatService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.UUID;

@RestController
@RequestMapping("")
public class ChatController {
    private final BotService botService;
    private final BotOrchestratorService botOrchestratorService;
    ConversationService conversationService;
    OrderService orderService;
    OrderOrchestrationService orderOrchestrationService;
    private final ChatService chatService;
    private final JwtManager jwtManager;

    public ChatController(ConversationService conversationService, ChatService chatService, JwtManager jwtManager, OrderOrchestrationService orderOrchestrationService, OrderService orderService, BotService botService, BotOrchestratorService botOrchestratorService) {
        this.conversationService = conversationService;
        this.chatService = chatService;
        this.orderService = orderService;
        this.jwtManager = jwtManager;
        this.orderOrchestrationService = orderOrchestrationService;
        this.botService = botService;
        this.botOrchestratorService = botOrchestratorService;
    }

    @GetMapping("/conversation/assigned")
    public ResponseEntity<?> getAssignedChat(@RequestParam Long workspaceId,
                                             @RequestParam(defaultValue = "1") Integer page,
                                             @RequestParam(defaultValue = "10") Integer limit,
                                             @RequestParam(required = false) Boolean isUnread,
                                             @RequestParam(required = false) String agent,
                                             @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm") LocalDateTime startDate,
                                             @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm") LocalDateTime endDate,
                                             @RequestParam(required = false) String statusOrder,
                                             @RequestParam(required = false) String keyword,
                                             @RequestParam(required = false) String statusPesan,
                                             @RequestParam(required = false) java.util.List<Long> labelId) {
        RestResponse rest = new RestResponse();
        try{
            var data = conversationService.getAssignedChat(workspaceId, page, limit, isUnread, agent, startDate, endDate, statusOrder, keyword, labelId);
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
    public ResponseEntity<?> getUnassignedChat(@RequestParam Long workspaceId,
                                               @RequestParam(defaultValue = "1") Integer page,
                                               @RequestParam(defaultValue = "10") Integer limit,
                                               @RequestParam(required = false) Boolean isUnread,
                                               @RequestParam(required = false) String agent,
                                               @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm") LocalDateTime startDate,
                                               @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm") LocalDateTime endDate,
                                               @RequestParam(required = false) String statusOrder,
                                               @RequestParam(required = false) String keyword,
                                               @RequestParam(required = false) String statusPesan,
                                               @RequestParam(required = false) java.util.List<Long> labelId) {
        RestResponse rest = new RestResponse();
        try{
            var data = conversationService.getUnassignedChat(workspaceId, page, limit, isUnread, agent, startDate, endDate, statusOrder, keyword, statusPesan, labelId);
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
    public ResponseEntity<?> getListChat(@RequestParam UUID conversationId
            , @RequestParam(defaultValue = "1") Integer page
            , @RequestParam(defaultValue = "10") Integer limit
            , @RequestParam(required = false) String keyword) {
        RestResponse rest = new RestResponse();
        try{
            var data = conversationService.getListMessage(conversationId, page, limit, keyword);
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

    @PostMapping("conversation/add-order")
    public ResponseEntity<?> addOrder(@RequestBody ChatAddOrderRequest payload,  HttpServletRequest request) {
        RestResponse rest = new RestResponse();
        try{
            String username = jwtManager.getUsernameByToken(request.getHeader("Authorization").substring(7));

           orderOrchestrationService.createOrderOnChat(payload, username);
            rest.setSuccess(true);
            rest.setMessage("Success");
            return ResponseEntity.ok(rest);
        }catch (Exception e){
            rest.setSuccess(false);
            rest.setMessage(e.getMessage());
            rest.setData(null);
            return ResponseEntity.badRequest().body(rest);
        }
    }

    @GetMapping("/agent")
    public ResponseEntity<?> getAgentList(@RequestParam(required = true) Long workspaceId){
        RestResponse restResponse = new RestResponse();
        try{
            restResponse.setSuccess(true);
            restResponse.setMessage("Success");
            restResponse.setData(conversationService.getChatAgent(workspaceId));
            return ResponseEntity.ok(restResponse);
        }catch (Exception e){
            e.printStackTrace();
            restResponse.setSuccess(false);
            restResponse.setMessage(e.getMessage());
            restResponse.setData(null);
            return ResponseEntity.badRequest().body(restResponse);
        }
    }

    @GetMapping("/chat/status")
    public ResponseEntity<?> getStatusList(){
        RestResponse restResponse = new RestResponse();
        try{
            restResponse.setSuccess(true);
            restResponse.setMessage("Success");
            var status = Arrays.stream(ChatStatus.values()).map(Enum::name).toList();
            restResponse.setData(status);
            return ResponseEntity.ok(restResponse);
        }catch (Exception e){
            e.printStackTrace();
            restResponse.setSuccess(false);
            restResponse.setMessage(e.getMessage());
            restResponse.setData(null);
            return ResponseEntity.badRequest().body(restResponse);
        }
    }






}
