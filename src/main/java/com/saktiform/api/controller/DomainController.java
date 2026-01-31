package com.saktiform.api.controller;

import com.saktiform.api.model.RestResponse;
import com.saktiform.api.model.domain.DeleteDomainPayload;
import com.saktiform.api.model.domain.UpsertDomainPayload;
import com.saktiform.api.service.DomainService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/domain")
public class DomainController {
    private final DomainService domainService;
    public DomainController(DomainService domainService) {
        this.domainService = domainService;
    }

    @PostMapping("/upsert")
    public ResponseEntity<?> upsertDomain(@RequestBody UpsertDomainPayload domain) {
        RestResponse rest = new RestResponse();
        try{
            domainService.upsertDomain(domain);
            rest.setSuccess(true);
            rest.setMessage("Upsert success");
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

    @GetMapping("/list")
    public ResponseEntity<?> getDomainList(@RequestParam(defaultValue = "1") Integer page,
                                           @RequestParam(defaultValue = "10") Integer limit,
                                           @RequestParam Long workspaceId) {
        RestResponse rest = new RestResponse();
        try{
            rest.setData(domainService.getListDomain(workspaceId, page, limit));
            rest.setSuccess(true);
            rest.setMessage("success");
            return ResponseEntity.ok(rest);
        }catch (Exception e){
            rest.setSuccess(false);
            rest.setMessage(e.getMessage());
            rest.setData(null);
            return ResponseEntity.badRequest().body(rest);
        }
    }

    @GetMapping("/dropdown")
    public ResponseEntity<?> getDomainList(@RequestParam Long workspaceId) {
        RestResponse rest = new RestResponse();
        try{
            rest.setData(domainService.getDomainDropDown(workspaceId));
            rest.setSuccess(true);
            rest.setMessage("success");
            return ResponseEntity.ok(rest);
        }catch (Exception e){
            rest.setSuccess(false);
            rest.setMessage(e.getMessage());
            rest.setData(null);
            return ResponseEntity.badRequest().body(rest);
        }
    }

    @PostMapping("/delete")
    public ResponseEntity<?> deleteDomain(@RequestBody DeleteDomainPayload payload){
        RestResponse rest = new RestResponse();
        try{
            domainService.deleteDomain(payload.getId());
            rest.setSuccess(true);
            rest.setMessage("success");
            return ResponseEntity.ok(rest);
        }catch (Exception e){
            rest.setSuccess(false);
            rest.setMessage(e.getMessage());
            rest.setData(null);
            return ResponseEntity.badRequest().body(rest);
        }
    }
}
