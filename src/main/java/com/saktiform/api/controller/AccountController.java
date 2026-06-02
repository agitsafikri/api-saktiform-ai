package com.saktiform.api.controller;

import com.saktiform.api.configuration.JwtManager;
import com.saktiform.api.entity.Account;
import com.saktiform.api.model.RestResponse;
import com.saktiform.api.model.LoginRequest;
import com.saktiform.api.model.LoginResponse;
import com.saktiform.api.model.account.Role;
import com.saktiform.api.model.account.DeleteAccountDto;
import com.saktiform.api.model.account.RegisterRequest;
import com.saktiform.api.model.account.ResetPasswordDto;
import com.saktiform.api.service.AccountService;
import com.saktiform.api.service.WorkspaceService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;

@RestController
@RequestMapping("/account")
public class AccountController {

    private final AccountService accountService;
    private final AuthenticationManager authenticationManager;
    private final JwtManager jwtManager;
    private final PasswordEncoder passwordEncoder;
    private final WorkspaceService workspaceService;

    public AccountController(AccountService accountService, AuthenticationManager authenticationManager, JwtManager jwtManager, PasswordEncoder passwordEncoder, WorkspaceService workspaceService) {
        this.accountService = accountService;
        this.authenticationManager = authenticationManager;
        this.jwtManager = jwtManager;
        this.passwordEncoder = passwordEncoder;
        this.workspaceService = workspaceService;
    }


    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        RestResponse rest = new RestResponse();
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
            );

            UserDetails userDetails = (UserDetails) authentication.getPrincipal();


            Account account = accountService.findByUsername(userDetails.getUsername())
                    .orElseThrow(() -> new UsernameNotFoundException("User not found"));
            if (account.getIsDeleted() != null && account.getIsDeleted() ){
                throw new UsernameNotFoundException("User not found");
            }

            String token = jwtManager.generateToken(userDetails, account);
            rest.setSuccess(true);
            rest.setMessage("Login success");
            var workspaces = workspaceService.getWorkspaceDropdownByUsername(userDetails.getUsername());
            rest.setData(new LoginResponse(token, userDetails.getUsername(), account.getNama(), account.getRole().name(), workspaces));
            return ResponseEntity.ok(rest);

        } catch (BadCredentialsException e) {
            rest.setSuccess(false);
            rest.setMessage("Invalid username or password");
            rest.setData(null);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(rest);
        }
    }

    @PostMapping("")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest request, BindingResult bindingResult) {
        RestResponse rest = new RestResponse();
        if (bindingResult.hasErrors()) {
            rest.setSuccess(false);
            rest.setMessage(bindingResult.getAllErrors().get(0).getDefaultMessage());
            rest.setData(null);
            return ResponseEntity.badRequest().body(rest);
        }
        try {

            accountService.registerAccount(request);
            rest.setSuccess(true);
            rest.setMessage("data successfully saved");
            rest.setData(null);
            return ResponseEntity.ok(rest);
        } catch (Exception e) {
            e.printStackTrace();
            rest.setSuccess(false);
            rest.setMessage(e.getMessage());
            rest.setData(null);
            return ResponseEntity.badRequest().body(rest);
        }
    }

    @GetMapping("")
    public ResponseEntity<?> getAllAccount(@RequestParam(defaultValue = "1") Integer page,
                                           @RequestParam(defaultValue = "10") Integer limit,
                                           @RequestParam(required = false) String search) {
        RestResponse rest = new RestResponse();
        try {
            var listAccount = accountService.getListAccount(page, limit, search);
            rest.setSuccess(true);
            rest.setMessage("Success");
            rest.setData(listAccount);
            return ResponseEntity.ok(rest);
        } catch (Exception e) {
            e.printStackTrace();
            rest.setSuccess(false);
            rest.setMessage(e.getMessage());
            rest.setData(null);
            return ResponseEntity.badRequest().body(rest);
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getDetailAccount(@PathVariable Long id) {
        RestResponse rest = new RestResponse();
        try {
            var data = accountService.getAccountById(id);
            rest.setSuccess(true);
            rest.setMessage("Success");
            rest.setData(data);
            return ResponseEntity.ok(rest);
        } catch (Exception e) {
            e.printStackTrace();
            rest.setSuccess(false);
            rest.setMessage(e.getMessage());
            rest.setData(null);
            return ResponseEntity.badRequest().body(rest);
        }
    }

    @GetMapping("/role")
    public ResponseEntity<?> getListRole(){
        RestResponse restResponse = new RestResponse();
        try{
            restResponse.setSuccess(true);
            restResponse.setMessage("Success");
            var role = Arrays.stream(Role.values()).map(Enum::name).toList();
            restResponse.setData(role);
            return ResponseEntity.ok(restResponse);
        }catch (Exception e){
            e.printStackTrace();
            restResponse.setSuccess(false);
            restResponse.setMessage(e.getMessage());
            restResponse.setData(null);
            return ResponseEntity.badRequest().body(restResponse);
        }
    }

    @GetMapping("/list")
    public ResponseEntity<?> getAvailableAccount() {
        RestResponse rest = new RestResponse();

        try{
            var workspace = accountService.getAccountDropdownList();
            rest.setData(workspace);
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

    @PostMapping("delete")
    public ResponseEntity<?> deleteAccount(@RequestBody DeleteAccountDto idAccount) {
        RestResponse rest = new RestResponse();
        try {
            accountService.deleteAccount(idAccount.getId());
            rest.setSuccess(true);
            rest.setMessage("success");
            return ResponseEntity.ok(rest);
        }catch (Exception e){
            rest.setMessage("Delete failed, please try again later");
            rest.setSuccess(false);
            rest.setMessage(e.getMessage());
            return ResponseEntity.badRequest().body(rest);
        }
    }

    @PostMapping("reset-password")
    public ResponseEntity<?> resetPassword(@RequestBody ResetPasswordDto resetPasswordDto){
        RestResponse rest = new RestResponse();
        try{
            accountService.resetPassword(resetPasswordDto);
            rest.setSuccess(true);
            rest.setMessage("success");
            return ResponseEntity.ok(rest);
        }catch (Exception e){
            rest.setMessage("Reset password failed, please try again later");
            rest.setSuccess(false);
            rest.setMessage(e.getMessage());
            return ResponseEntity.badRequest().body(rest);
        }
    }


}
