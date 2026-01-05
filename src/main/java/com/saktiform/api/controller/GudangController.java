package com.saktiform.api.controller;

import com.saktiform.api.model.RestResponse;
import com.saktiform.api.model.gudang.AddGudangDto;
import com.saktiform.api.service.GudangService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/gudang")
public class GudangController {
    private final GudangService gudangService;
    public GudangController(GudangService appConfigService) {
        this.gudangService = appConfigService;
    }

    @PostMapping()
    public ResponseEntity<?> upsertGudang(@RequestBody AddGudangDto request) {
        RestResponse rest = new RestResponse();
        try {
            gudangService.upsertGudang(request);
            rest.setSuccess(true);
            return ResponseEntity.ok(rest);
        }catch (Exception e) {
            e.printStackTrace();
            rest.setSuccess(false);
            rest.setMessage(e.getMessage());
            rest.setData(null);
            return ResponseEntity.badRequest().body(rest);
        }
    }

    @GetMapping
    public ResponseEntity<?> get (@RequestParam Long workspaceId){
        RestResponse rest = new RestResponse();
        try{
            var listGudang = gudangService.getGudangByWorkspaceId(workspaceId);
            rest.setSuccess(true);
            rest.setMessage("Get success");
            rest.setData(listGudang);
        }catch (Exception e){
            e.printStackTrace();
            rest.setSuccess(false);
            rest.setMessage(e.getMessage());
            rest.setData(null);
            return ResponseEntity.badRequest().body(rest);
        }
        return ResponseEntity.ok(rest);
    }

    @GetMapping("/delete")
    public ResponseEntity<?> deleteGudang(@RequestParam Long id){
        RestResponse rest = new RestResponse();
        try{
            gudangService.deleteGudangById(id);
            rest.setSuccess(true);
            rest.setMessage("Delete success");
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

}
