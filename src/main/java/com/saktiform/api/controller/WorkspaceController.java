package com.saktiform.api.controller;

import com.saktiform.api.model.RestResponse;
import com.saktiform.api.model.account.AddListAccountToWorkspace;
import com.saktiform.api.model.domain.SetDomainToWorkspaceRequest;
import com.saktiform.api.model.workspace.AddWorkspaceDto;
import com.saktiform.api.model.workspace.UpdateWorkspaceDto;
import com.saktiform.api.service.WorkspaceService;
import com.saktiform.api.util.MapperHelper;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/workspace")
public class WorkspaceController {
    private final WorkspaceService workspaceService;

    public WorkspaceController(WorkspaceService workspaceService) {
        this.workspaceService = workspaceService;
    }

    @PostMapping()
    public ResponseEntity<?> addWorkspace(@Valid @RequestBody AddWorkspaceDto workspaceDTO, BindingResult bindingResult) {
        if(bindingResult.hasErrors()){
            RestResponse rest = new RestResponse();
            rest.setSuccess(false);
            rest.setMessage("Error Validasi");
            rest.setData(MapperHelper.getErrors(bindingResult.getAllErrors()));
            return ResponseEntity.badRequest().body(rest);
        }

        RestResponse rest = new RestResponse();

        try {
            workspaceService.upsertWorkspace(workspaceDTO);
            rest.setSuccess(true);
            return ResponseEntity.ok(rest);
        } catch (Exception e) {
            rest.setSuccess(false);
            rest.setMessage(e.getMessage());
            rest.setData(null);
            return ResponseEntity.badRequest().body(rest);
        }
    }

    @PostMapping("/update")
    public ResponseEntity<?> updateWorkspace(@Valid @RequestBody UpdateWorkspaceDto data, BindingResult bindingResult) {
        if(bindingResult.hasErrors()){
            RestResponse rest = new RestResponse();
            rest.setSuccess(false);
            rest.setMessage("Error Validasi");
            rest.setData(MapperHelper.getErrors(bindingResult.getAllErrors()));
            return ResponseEntity.badRequest().body(rest);
        }

        RestResponse rest = new RestResponse();

        try {
            workspaceService.updateWorkspace(data);
            rest.setSuccess(true);
            return ResponseEntity.ok(rest);
        } catch (Exception e) {
            rest.setSuccess(false);
            rest.setMessage(e.getMessage());
            rest.setData(null);
            return ResponseEntity.badRequest().body(rest);
        }
    }


    @GetMapping()
    public ResponseEntity<?> getAllWorkspace(@RequestParam(defaultValue = "1") Integer page,
                                             @RequestParam(defaultValue = "10") Integer limit) {

        RestResponse rest = new RestResponse();

        try {
            var listWorkspace = workspaceService.getListWorkspace(page, limit);
            rest.setSuccess(true);
            rest.setData(listWorkspace);
            return ResponseEntity.ok(rest);
        } catch (Exception e) {
            rest.setSuccess(false);
            rest.setMessage(e.getMessage());
            rest.setData(null);
            return ResponseEntity.badRequest().body(rest);
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getWorkspaceById(@PathVariable Long id) {
        RestResponse rest = new RestResponse();

        try{
            var workspace = workspaceService.getWorkspaceById(id);
            rest.setSuccess(true);
            rest.setData(workspace);
            return ResponseEntity.ok(rest);
        }catch (Exception e) {
            rest.setSuccess(false);
            rest.setMessage(e.getMessage());
            rest.setData(null);
            return ResponseEntity.badRequest().body(rest);
        }
    }

    @GetMapping("/list")
    public ResponseEntity<?> getAvailableWorkspace() {
        RestResponse rest = new RestResponse();

        try{
            var workspace = workspaceService.getWorkspaceDropdownList();
            rest.setData(workspace);
            rest.setSuccess(true);
            return ResponseEntity.ok(rest);
        }catch (Exception e) {
            rest.setSuccess(false);
            rest.setMessage(e.getMessage());
            rest.setData(null);
            return ResponseEntity.badRequest().body(rest);
        }
    }

    @GetMapping("/{id}/account")
    public ResponseEntity<?> getAccountInWorkspaceById(@PathVariable Long id, @RequestParam(defaultValue = "1") Integer page,
                                                       @RequestParam(defaultValue = "10") Integer limit) {
        RestResponse rest = new RestResponse();

        try{
            var workspace = workspaceService.getWorkspaceAccountList(id);
            rest.setSuccess(true);
            rest.setData(workspace);
            return ResponseEntity.ok(rest);
        }catch (Exception e) {
            rest.setSuccess(false);
            rest.setMessage(e.getMessage());
            rest.setData(null);
            return ResponseEntity.badRequest().body(rest);
        }
    }

    @GetMapping("/{id}/account/remove")
    public ResponseEntity<?> removeAccountFromWorkspace(@PathVariable Long id, @RequestParam Long idAccount) {
        RestResponse rest = new RestResponse();

        try{
            workspaceService.removeAccountFromWorkspace(id, idAccount);
            rest.setSuccess(true);
            rest.setMessage("success");
            return ResponseEntity.ok(rest);
        }catch (Exception e) {
            rest.setSuccess(false);
            rest.setMessage(e.getMessage());
            rest.setData(null);
            return ResponseEntity.badRequest().body(rest);
        }
    }

    @PostMapping("/{id}/account")
    public ResponseEntity<?> getAccountInWorkspaceById(@PathVariable Long id, @RequestBody AddListAccountToWorkspace accountList) {
        RestResponse rest = new RestResponse();

        try{
            var workspace = workspaceService.addAccountListToWorkSpace(id, accountList);
            rest.setSuccess(true);
            rest.setData(workspace);
            return ResponseEntity.ok(rest);
        }catch (Exception e) {
            rest.setSuccess(false);
            rest.setMessage(e.getMessage());
            rest.setData(null);
            return ResponseEntity.badRequest().body(rest);
        }
    }

    @GetMapping("/{id}/domain")
    public ResponseEntity<?> getWorkspaceDomain(@PathVariable Long id) {
        RestResponse rest = new RestResponse();

        try{
            var workspace = workspaceService.getActiveDomainByWorkspaceId(id);
            rest.setSuccess(true);
            rest.setMessage("success");
            rest.setData(workspace);
            return ResponseEntity.ok(rest);
        }catch (Exception e) {
            rest.setSuccess(false);
            rest.setMessage(e.getMessage());
            rest.setData(null);
            return ResponseEntity.badRequest().body(rest);
        }
    }

    @PostMapping("/domain")
    public ResponseEntity<?> setDomainToWorkspace(@RequestBody SetDomainToWorkspaceRequest payload) {
        RestResponse rest = new RestResponse();
        try{
            rest.setSuccess(true);
            rest.setMessage("success");
            workspaceService.setDomainToWorkspace(payload);
            return ResponseEntity.ok(rest);
        }catch (Exception e) {
            rest.setSuccess(false);
            rest.setMessage(e.getMessage());
            rest.setData(null);
            return ResponseEntity.badRequest().body(rest);
        }
    }
}
