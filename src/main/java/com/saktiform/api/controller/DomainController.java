package com.saktiform.api.controller;

import com.saktiform.api.model.RestResponse;
import com.saktiform.api.model.domain.DomainDto;
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
    public ResponseEntity<?> upsertDomain(@RequestBody DomainDto domain) {
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
    public ResponseEntity<?> getDomainList(@RequestParam Long page, @RequestParam Long limit) {
        return null;
    }
}
