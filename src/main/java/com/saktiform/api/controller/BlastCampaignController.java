package com.saktiform.api.controller;

import com.saktiform.api.configuration.JwtManager;
import com.saktiform.api.model.RestResponse;
import com.saktiform.api.model.blast.request.CreateCampaignRequest;
import com.saktiform.api.model.blast.request.RetryRequest;
import com.saktiform.api.model.blast.response.CampaignDetailDto;
import com.saktiform.api.model.blast.response.RetryResponse;
import com.saktiform.api.repository.AccountRepository;
import com.saktiform.api.service.blast.BlastCampaignService;
import com.saktiform.api.service.blast.BlastReportService;
import com.saktiform.api.service.blast.BlastRetryService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/blast/campaign")
public class BlastCampaignController {

    private final BlastCampaignService campaignService;
    private final BlastReportService reportService;
    private final BlastRetryService retryService;
    private final JwtManager jwtManager;
    private final AccountRepository accountRepository;

    public BlastCampaignController(BlastCampaignService campaignService,
                                   BlastReportService reportService,
                                   BlastRetryService retryService,
                                   JwtManager jwtManager,
                                   AccountRepository accountRepository) {
        this.campaignService = campaignService;
        this.reportService = reportService;
        this.retryService = retryService;
        this.jwtManager = jwtManager;
        this.accountRepository = accountRepository;
    }

    @PostMapping
    public ResponseEntity<?> create(@Valid @RequestBody CreateCampaignRequest request,
                                    @RequestParam Long workspaceId,
                                    HttpServletRequest httpRequest) {
        RestResponse rest = new RestResponse();
        try {
            Long accountId = resolveAccountId(httpRequest);
            var data = campaignService.create(request, workspaceId, accountId);
            rest.setSuccess(true);
            rest.setMessage("Success");
            rest.setData(data);
            return ResponseEntity.ok(rest);
        } catch (Exception e) {
            rest.setSuccess(false);
            rest.setMessage(e.getMessage());
            rest.setData(null);
            return ResponseEntity.badRequest().body(rest);
        }
    }

    @GetMapping
    public ResponseEntity<?> list(@RequestParam Long workspaceId,
                                  @RequestParam(defaultValue = "1") Integer page,
                                  @RequestParam(defaultValue = "10") Integer limit,
                                  @RequestParam(required = false) String search,
                                  @RequestParam(required = false) String status) {
        return execute(() -> campaignService.list(workspaceId, search, status, page, limit));
    }

    @GetMapping("/{campaignId}")
    public ResponseEntity<?> detail(@PathVariable UUID campaignId, @RequestParam Long workspaceId) {
        return execute(() -> campaignService.detail(campaignId, workspaceId));
    }

    @GetMapping("/{campaignId}/progress")
    public ResponseEntity<?> progress(@PathVariable UUID campaignId, @RequestParam Long workspaceId) {
        return execute(() -> campaignService.progress(campaignId, workspaceId));
    }

    @GetMapping("/{campaignId}/messages")
    public ResponseEntity<?> messages(@PathVariable UUID campaignId,
                                      @RequestParam Long workspaceId,
                                      @RequestParam(required = false) String status,
                                      @RequestParam(defaultValue = "1") Integer page,
                                      @RequestParam(defaultValue = "20") Integer limit) {
        return execute(() -> campaignService.messages(campaignId, workspaceId, status, page, limit));
    }

    @GetMapping("/{campaignId}/review")
    public ResponseEntity<?> review(@PathVariable UUID campaignId, @RequestParam Long workspaceId) {
        return execute(() -> campaignService.review(campaignId, workspaceId));
    }

    @PostMapping("/{campaignId}/start")
    public ResponseEntity<?> start(@PathVariable UUID campaignId, @RequestParam Long workspaceId) {
        return execute(() -> campaignService.start(campaignId, workspaceId));
    }

    @PostMapping("/{campaignId}/pause")
    public ResponseEntity<?> pause(@PathVariable UUID campaignId, @RequestParam Long workspaceId) {
        return execute(() -> campaignService.pause(campaignId, workspaceId));
    }

    @PostMapping("/{campaignId}/resume")
    public ResponseEntity<?> resume(@PathVariable UUID campaignId, @RequestParam Long workspaceId) {
        return execute(() -> campaignService.resume(campaignId, workspaceId));
    }

    @PostMapping("/{campaignId}/cancel")
    public ResponseEntity<?> cancel(@PathVariable UUID campaignId, @RequestParam Long workspaceId) {
        return execute(() -> campaignService.cancel(campaignId, workspaceId));
    }

    @PostMapping("/{campaignId}/retry")
    public ResponseEntity<?> retry(@PathVariable UUID campaignId,
                                   @RequestParam Long workspaceId,
                                   @RequestBody(required = false) RetryRequest request) {
        return execute(() -> new RetryResponse(retryService.retry(
                campaignId, request != null ? request.getMessageIds() : null, workspaceId)));
    }

    @GetMapping("/{campaignId}/report")
    public void report(@PathVariable UUID campaignId, @RequestParam Long workspaceId,
                       HttpServletResponse response) throws java.io.IOException {
        try {
            CampaignDetailDto detail = campaignService.detail(campaignId, workspaceId); // validasi + nama
            String filename = reportService.buildFileName(detail.getName());
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");
            reportService.generateReport(campaignId, workspaceId, response.getOutputStream());
            response.flushBuffer();
        } catch (Exception e) {
            if (!response.isCommitted()) {
                response.reset();
                response.setStatus(HttpStatus.BAD_REQUEST.value());
                response.setContentType("application/json");
                String msg = e.getMessage() == null ? "error" : e.getMessage().replace("\"", "'");
                response.getWriter().write("{\"success\":false,\"message\":\"" + msg + "\"}");
            }
        }
    }

    // ---- helpers ----

    private ResponseEntity<?> execute(java.util.function.Supplier<Object> action) {
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

    private Long resolveAccountId(HttpServletRequest request) {
        try {
            String auth = request.getHeader("Authorization");
            if (auth == null || !auth.startsWith("Bearer ")) return null;
            String username = jwtManager.getUsernameByToken(auth.substring(7));
            return accountRepository.findByUsername(username).map(a -> a.getId()).orElse(null);
        } catch (Exception e) {
            return null;
        }
    }
}
