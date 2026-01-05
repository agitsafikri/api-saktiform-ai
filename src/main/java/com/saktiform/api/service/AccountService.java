package com.saktiform.api.service;

import com.saktiform.api.configuration.JwtManager;
import com.saktiform.api.entity.Account;
import com.saktiform.api.model.Role;
import com.saktiform.api.entity.Workspace;
import com.saktiform.api.model.account.*;
import com.saktiform.api.model.workspace.WorkspaceDropdownDto;
import com.saktiform.api.repository.AccountRepository;
import com.saktiform.api.repository.WorkspaceRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

@Service
public class AccountService {
    private final AuthenticationManager authenticationManager;
    private final JwtManager jwtManager;
    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;
    private final WorkspaceRepository workspaceRepository;

    public AccountService(AuthenticationManager authenticationManager,
                          JwtManager jwtManager,
                          AccountRepository accountRepository,
                          PasswordEncoder passwordEncoder,
                          WorkspaceRepository workspaceRepository) {
        this.authenticationManager = authenticationManager;
        this.jwtManager = jwtManager;
        this.accountRepository = accountRepository;
        this.passwordEncoder = passwordEncoder;
        this.workspaceRepository = workspaceRepository;

    }

    public void registerAccount(RegisterRequest data) {
        Account account;
        if (data.getId() != null) {
            account = accountRepository.findById(data.getId())
                    .orElseThrow(() -> new RuntimeException("Account not found"));
        } else {
            account = new Account();
            account.setCreatedAt(Instant.now());
            account.setIsDeleted(false);
        }

        if (data.getId() == null && findByUsername(data.getUsername()).isPresent()  ) {
            throw new IllegalArgumentException("Username already exists");
        }

        if (data.getId() != null && !accountRepository.findById(data.getId()).get().getUsername().equals(data.getUsername().toLowerCase()) ) {
            if (findByUsername(data.getUsername()).isPresent()) {
                throw new IllegalArgumentException("Username already exists");
            }
        }

        account.setId(data.getId() != null ? data.getId() : null);
        account.setNama(data.getNama());
        account.setUsername(data.getUsername().toLowerCase());
        if (data.getPassword() != null && !data.getPassword().isEmpty())account.setPassword(passwordEncoder.encode(data.getPassword()));
        account.setRole(Role.valueOf(data.getRole().name()));

        if (data.getIdWorkspaces() != null) {
            List<Workspace> workspaces = workspaceRepository.findAllById(data.getIdWorkspaces());
            account.setWorkspaces(new HashSet<>(workspaces));
        }

        accountRepository.save(account);
    }

    public Page<AccountListDto> getListAccount (Integer page, Integer limit) {
        var pageable = PageRequest.of(page - 1 , limit, Sort.by(Sort.Direction.ASC, "nama"));

        return accountRepository.getAccountList(pageable);
    }

    public DetailAccountDto getAccountById(Long id) {

        var account = accountRepository.findById(id).get();

        DetailAccountDto data = new DetailAccountDto();
        data.setId(account.getId());
        data.setUsername(account.getUsername());
        data.setNama(account.getNama());
        data.setRole(account.getRole().name());
        if(!account.getWorkspaces().isEmpty()){
            account.getWorkspaces().forEach((n)->{
                data.getWorkspaces().add(new WorkspaceAccount(n.getId(), n.getNamaWorkspace()));
            });
        }

        return data;
    }

    public Optional<Account> findByUsername(String username) {
        return accountRepository.findByUsername(username);
    }

    public List<AccountDropdownDto> getAccountDropdownList(){
        var account = accountRepository.getAccountDropdown();
        return account;
    }

    public void deleteAccount(Long id){
        accountRepository.updateIsDeletedById(id);
    }

    public void resetPassword(ResetPasswordDto resetPasswordDto){
        var account = accountRepository.findById(resetPasswordDto.getId()).get();
        account.setPassword(passwordEncoder.encode(resetPasswordDto.getNewPassword()));
        accountRepository.save(account);
    }
}
