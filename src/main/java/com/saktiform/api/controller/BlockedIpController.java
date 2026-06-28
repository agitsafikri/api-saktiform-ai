package com.saktiform.api.controller;

import com.saktiform.api.model.RestResponse;
import com.saktiform.api.model.blockedip.CreateBlockedIpDto;
import com.saktiform.api.service.BlockedIpService;
import com.saktiform.api.util.IpUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/master/blocked-ip")
public class BlockedIpController {

    private final BlockedIpService blockedIpService;

    public BlockedIpController(BlockedIpService blockedIpService) {
        this.blockedIpService = blockedIpService;
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody CreateBlockedIpDto data, HttpServletRequest request) {
        RestResponse rest = new RestResponse();
        try {
            String requesterIp = IpUtil.resolveClientIp(request);
            rest.setSuccess(true);
            rest.setMessage("Success");
            rest.setData(blockedIpService.create(data.getIpAddress(), requesterIp));
            return ResponseEntity.ok(rest);
        } catch (Exception e) {
            rest.setSuccess(false);
            rest.setMessage(e.getMessage());
            rest.setData(null);
            return ResponseEntity.badRequest().body(rest);
        }
    }

    @GetMapping
    public ResponseEntity<?> list(@RequestParam(defaultValue = "1") Integer page, @RequestParam(defaultValue = "10") Integer limit) {
        RestResponse rest = new RestResponse();
        try {
            rest.setSuccess(true);
            rest.setMessage("Success");
            rest.setData(blockedIpService.list(page, limit));
            return ResponseEntity.ok(rest);
        } catch (Exception e) {
            rest.setSuccess(false);
            rest.setMessage(e.getMessage());
            rest.setData(null);
            return ResponseEntity.badRequest().body(rest);
        }
    }

    @GetMapping("/delete")
    public ResponseEntity<?> delete(@RequestParam Long id) {
        RestResponse rest = new RestResponse();
        try {
            blockedIpService.delete(id);
            rest.setSuccess(true);
            rest.setMessage("Success");
            rest.setData(null);
            return ResponseEntity.ok(rest);
        } catch (Exception e) {
            rest.setSuccess(false);
            rest.setMessage(e.getMessage());
            rest.setData(null);
            return ResponseEntity.badRequest().body(rest);
        }
    }
}
