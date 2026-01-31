package com.saktiform.api.service;

import com.saktiform.api.entity.Account;
import com.saktiform.api.entity.ChatTemplate;
import com.saktiform.api.entity.Gudang;
import com.saktiform.api.entity.Workspace;
import com.saktiform.api.model.Role;
import com.saktiform.api.model.account.AccountDropdownDto;
import com.saktiform.api.model.account.AddListAccountToWorkspace;
import com.saktiform.api.model.domain.DomainDto;
import com.saktiform.api.model.domain.SetDomainToWorkspaceRequest;
import com.saktiform.api.model.workspace.*;
import com.saktiform.api.repository.AccountRepository;
import com.saktiform.api.repository.ChatTemplateRepository;
import com.saktiform.api.repository.GudangRepository;
import com.saktiform.api.repository.WorkspaceRepository;
import com.saktiform.api.service.chat.MessageConstructorHelper;
import jakarta.transaction.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class WorkspaceService {
    private final WorkspaceRepository workspaceRepository;
    private final GudangRepository gudangRepository;
    private final AccountRepository accountRepository;
    private final MessageConstructorHelper messageConstructorHelper;
    private final ChatTemplateRepository chatTemplateRepository;
    private final DomainService domainService;

    public WorkspaceService(WorkspaceRepository workspaceRepository, GudangRepository gudangRepository, AccountRepository accountRepository, MessageConstructorHelper messageConstructorHelper, ChatTemplateRepository chatTemplateRepository, DomainService domainService) {
        this.workspaceRepository = workspaceRepository;
        this.gudangRepository = gudangRepository;
        this.accountRepository = accountRepository;
        this.messageConstructorHelper = messageConstructorHelper;
        this.chatTemplateRepository = chatTemplateRepository;
        this.domainService = domainService;

    }

    @Transactional
    public void upsertWorkspace(AddWorkspaceDto data) {
        if(!checkWabaAvailibility(data.getWabaId())){
            throw new IllegalArgumentException("Waba ID sudah terdaftar");
        }

        Workspace workspace = new Workspace();
        workspace.setCreatedAt(Instant.now());
        workspace.setNamaWorkspace(data.getNamaWorkspace());
        workspace.setWabaId(data.getWabaId());

        var savedWorkspace = workspaceRepository.save(workspace);

        if(!data.getIdUsers().isEmpty()){
            List<Account> accounts = accountRepository.findAllById(data.getIdUsers());
            for (Account account : accounts) {
                account.getWorkspaces().add(savedWorkspace);
                accountRepository.save(account);
                savedWorkspace.getAccounts().add(account);
                workspaceRepository.save(savedWorkspace);
            }
        }

        Gudang gudang;
        var defaultGudang = gudangRepository.findByIdWorkspaceAndIsDefault(savedWorkspace.getId(), true);
        if (defaultGudang == null) {
            gudang = new Gudang();
        }else {
            gudang = defaultGudang;
        }


        gudang.setIdWorkspace(savedWorkspace.getId());
        gudang.setNamaGudang(data.getGudang().getNamaGudang());
        gudang.setAlamat(data.getGudang().getAlamat());
        gudang.setIdProvinsi(data.getGudang().getIdProvinsi());
        gudang.setIdKota(data.getGudang().getIdKota());
        gudang.setIdKecamatan(data.getGudang().getIdKecamatan());
        gudang.setCreatedAt(Instant.now());
        gudang.setIsDeleted(false);
        gudang.setIsDefault(true);

        gudangRepository.save(gudang);


        ChatTemplate chatTemplate = new ChatTemplate();
        chatTemplate.setNamaTemplate("Follow UP Order");
        chatTemplate.setCategory("FOLLOWUP");
        chatTemplate.setCreatedAt(Instant.now());
        chatTemplate.setIdWorkspace(savedWorkspace.getId());
        chatTemplate.setContent(messageConstructorHelper.createFollowupMessage());

        chatTemplateRepository.save(chatTemplate);
    }

    public void updateWorkspace(UpdateWorkspaceDto data){
        if(!checkWabaAvailibility(data.getWabaId())){
            throw new IllegalArgumentException("Waba ID sudah terdaftar");
        }
        Workspace workspace = workspaceRepository.findById(data.getId()).get();
        workspace.setNamaWorkspace(data.getNamaWorkspace());
        workspace.setWabaId(data.getWabaId());
        workspaceRepository.save(workspace);
    }


    public Page<?> getListWorkspace(Integer page, Integer limit) {
        var pageable = PageRequest.of(page - 1 , limit, Sort.by(Sort.Direction.ASC, "namaWorkspace"));
        return workspaceRepository.getWorkspaceList(pageable);
    }

    public DetailWorkspace getWorkspaceById(Long id) {
        var workspace = workspaceRepository.findById(id).get();

        DetailWorkspace data = new DetailWorkspace();
        data.setId(workspace.getId());
        data.setNamaWorkspace(workspace.getNamaWorkspace());
        data.setWabaId(workspace.getWabaId());

        var gudang = gudangRepository.findByIdWorkspaceAndIsDefault(workspace.getId(), true);
        if (gudang != null) {
            data.setGudang(new GudangDto(
                    gudang.getNamaGudang(),
                    gudang.getAlamat(),
                    gudang.getIdProvinsi(), gudang.getIdKota(), gudang.getIdKecamatan()
            ));
        }

        var accounts = workspace.getAccounts();
        if (accounts != null) {
            for (Account account : accounts) {
                data.getUsers().add(new AccountDropdownDto(account.getId(), account.getUsername(), account.getNama()));
            }
        }

        return data;
    }

    public List<WorkspaceDropdownDto> getWorkspaceDropdownByUsername(String username){
        var account = accountRepository.findByUsername(username).get();
        List<Workspace> workspaces =  new ArrayList<>();
        if(account.getRole().equals(Role.OWNER)){
            workspaces = workspaceRepository.findAll();
        }else{
            workspaces = workspaceRepository.findAllById(account.getWorkspaces().stream().map(Workspace::getId).toList());
        }

        List<WorkspaceDropdownDto> dropdownWorkspace = new ArrayList<WorkspaceDropdownDto>();
        workspaces.forEach((n)->{
            dropdownWorkspace.add(new WorkspaceDropdownDto(n.getId(), n.getNamaWorkspace()));
        });

        return dropdownWorkspace;
    }

    public List<WorkspaceDropdownDto> getWorkspaceDropdownList(){
        var workspace = workspaceRepository.getWorkspaceDropdown();
        return workspace;
    }

    public Boolean checkWabaAvailibility(UUID wabaId){
        var workspace = workspaceRepository.findByWabaId(wabaId);

        if (workspace == null) {
            return true;
        }else {
            return false;
        }
    }

    public Workspace findByWabaId(UUID wabaId){
        return workspaceRepository.findByWabaId(wabaId);
    }

    public List<AccountDropdownDto> getAccountDropdownByWorkspaceId(Long idWorkspace){
        var accounts = accountRepository.findAllById(workspaceRepository.findById(idWorkspace).get().getAccounts().stream().map(Account::getId).toList());
        List<AccountDropdownDto> accountList = new ArrayList<>();
        return accounts.stream().map(account -> new AccountDropdownDto(account.getId(), account.getUsername(), account.getNama())).toList();
    }

    public List<WorkspaceAccountList> getWorkspaceAccountList(Long idWorkspace){
        var accounts = accountRepository.findAllById(workspaceRepository.findById(idWorkspace).get().getAccounts().stream().map(Account::getId).toList());
        List<AccountDropdownDto> accountList = new ArrayList<>();
        return accounts.stream().map(account -> new WorkspaceAccountList(account.getId(), account.getUsername(), account.getRole().name(), account.getNama())).toList();
    }

    public List<AccountDropdownDto> addAccountListToWorkSpace(Long idWorkspace, AddListAccountToWorkspace accountList){

        List<Account> accounts = accountRepository.findAllById(accountList.getAccountId());
        Workspace workspace = workspaceRepository.findById(idWorkspace).get();
        for (Account account : accounts) {
            account.getWorkspaces().add(workspace);
            accountRepository.save(account);
            workspace.getAccounts().add(account);
            workspaceRepository.save(workspace);
        }
        workspaceRepository.save(workspace);
        return accounts.stream().map(account -> new AccountDropdownDto(account.getId(), account.getUsername(), account.getNama())).toList();
    }

    public DomainDto getActiveDomainByWorkspaceId(Long idWorkspace){
        var workspace = workspaceRepository.getById(idWorkspace);

        if(workspace.getDomain() == null){
            throw new RuntimeException("Domain belum dipilih");
        }else {
            var domain = domainService.getDomainById(workspace.getIdDomain());
            return new DomainDto(domain.getId(), domain.getDomain());
        }

    }

    public void setDomainToWorkspace(SetDomainToWorkspaceRequest payload){
        var workspace = workspaceRepository.getById(payload.getIdWorkspace());

        workspace.setIdDomain(payload.getIdDomain());
        workspaceRepository.save(workspace);

    }

    public void removeAccountFromWorkspace(Long idWorkspace, Long idAccount){
        var workspace = workspaceRepository.getById(idWorkspace);
        var account = accountRepository.getById(idAccount);

        workspace.getAccounts().remove(account);
        account.getWorkspaces().remove(workspace);
        workspaceRepository.save(workspace);
        accountRepository.save(account);
    }




}
