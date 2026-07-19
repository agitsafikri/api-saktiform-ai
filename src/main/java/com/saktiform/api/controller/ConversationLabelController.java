package com.saktiform.api.controller;

import com.saktiform.api.model.RestResponse;
import com.saktiform.api.model.label.request.LabelRequest;
import com.saktiform.api.service.label.ConversationLabelService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.function.Supplier;

/**
 * Endpoint master label (workspace-scoped). Assignment ke conversation ditangani terpisah.
 */
@RestController
@RequestMapping("/chat/label")
public class ConversationLabelController {

    private final ConversationLabelService labelService;

    public ConversationLabelController(ConversationLabelService labelService) {
        this.labelService = labelService;
    }

    @PostMapping
    public ResponseEntity<?> create(@Valid @RequestBody LabelRequest request,
                                    @RequestParam Long workspaceId) {
        return execute(() -> labelService.create(request, workspaceId));
    }

    @GetMapping
    public ResponseEntity<?> list(@RequestParam Long workspaceId) {
        return execute(() -> labelService.list(workspaceId));
    }

    @PutMapping("/{labelId}")
    public ResponseEntity<?> update(@PathVariable Long labelId,
                                    @Valid @RequestBody LabelRequest request,
                                    @RequestParam Long workspaceId) {
        return execute(() -> labelService.update(labelId, request, workspaceId));
    }

    @DeleteMapping("/{labelId}")
    public ResponseEntity<?> delete(@PathVariable Long labelId,
                                    @RequestParam Long workspaceId) {
        return execute(() -> {
            labelService.delete(labelId, workspaceId);
            return "deleted";
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
