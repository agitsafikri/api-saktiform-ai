package com.saktiform.api.controller;

import com.saktiform.api.model.RestResponse;
import com.saktiform.api.model.label.request.AssignLabelRequest;
import com.saktiform.api.service.label.ConversationLabelService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Endpoint assign/unassign/list label pada sebuah conversation (workspace-scoped).
 */
@RestController
@RequestMapping("/chat/conversation/{conversationId}/label")
public class ConversationLabelAssignmentController {

    private final ConversationLabelService labelService;

    public ConversationLabelAssignmentController(ConversationLabelService labelService) {
        this.labelService = labelService;
    }

    @PostMapping
    public ResponseEntity<?> assign(@PathVariable UUID conversationId,
                                    @Valid @RequestBody AssignLabelRequest request,
                                    @RequestParam Long workspaceId) {
        return execute(() -> labelService.assign(conversationId, request.getLabelIds(), workspaceId));
    }

    @GetMapping
    public ResponseEntity<?> list(@PathVariable UUID conversationId,
                                  @RequestParam Long workspaceId) {
        return execute(() -> labelService.listForConversation(conversationId, workspaceId));
    }

    @DeleteMapping("/{labelId}")
    public ResponseEntity<?> unassign(@PathVariable UUID conversationId,
                                      @PathVariable Long labelId,
                                      @RequestParam Long workspaceId) {
        return execute(() -> {
            labelService.unassign(conversationId, labelId, workspaceId);
            return "unassigned";
        });
    }

    // ---- helper ----

    private ResponseEntity<?> execute(Supplier<Object> action) {
        RestResponse rest = new RestResponse();
        try {
            rest.setSuccess(true);
            rest.setMessage("Success");
            rest.setData(action.get());
            return ResponseEntity.ok(rest);
        } catch (Exception e) {
            rest.setSuccess(false);
            rest.setMessage(e.getMessage());
            rest.setData(null);
            return ResponseEntity.badRequest().body(rest);
        }
    }
}
