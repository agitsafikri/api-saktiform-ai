package com.saktiform.api.service;

import com.saktiform.api.entity.Account;
import com.saktiform.api.entity.ChatTemplate;
import com.saktiform.api.entity.Gudang;
import com.saktiform.api.entity.Workspace;
import com.saktiform.api.model.account.Role;
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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDateTime;
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
        var checkWorkspace = workspaceRepository.findByWabaId(data.getWabaId());
        if(checkWorkspace != null){
            throw new IllegalArgumentException("Waba sudah digunakan di workspace lain, silahkan gunakan waba yang lain.");
        }
        var waba = workspaceRepository.findWabaById(data.getWabaId());
        if(waba != null && waba.getStatus().equals("DISCONNECTED")){
            throw new IllegalArgumentException("Status waba DISCONNECTED, silahkan aktifkan terlebih dahulu atau gunakan waba yang lain.");
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
        chatTemplate.setCategory("FOLLOWUP-COD");
        chatTemplate.setCreatedAt(Instant.now());
        chatTemplate.setIdWorkspace(savedWorkspace.getId());
        chatTemplate.setContent(messageConstructorHelper.createFollowupCodMessage());
        chatTemplateRepository.save(chatTemplate);

        chatTemplate = new ChatTemplate();
        chatTemplate.setNamaTemplate("Confirmation  Order");
        chatTemplate.setCategory("CONFIRMATION-COD");
        chatTemplate.setCreatedAt(Instant.now());
        chatTemplate.setIdWorkspace(savedWorkspace.getId());
        chatTemplate.setContent(messageConstructorHelper.createConfirmationCodMessage());
        chatTemplateRepository.save(chatTemplate);

        chatTemplate = new ChatTemplate();
        chatTemplate.setNamaTemplate("Follow UP Order");
        chatTemplate.setCategory("FOLLOWUP-TRANSFER");
        chatTemplate.setCreatedAt(Instant.now());
        chatTemplate.setIdWorkspace(savedWorkspace.getId());
        chatTemplate.setContent(messageConstructorHelper.createFollowupTransferMessage());
        chatTemplateRepository.save(chatTemplate);

        chatTemplate = new ChatTemplate();
        chatTemplate.setNamaTemplate("Confirmation  Order");
        chatTemplate.setCategory("CONFIRMATION-TRANSFER");
        chatTemplate.setCreatedAt(Instant.now());
        chatTemplate.setIdWorkspace(savedWorkspace.getId());
        chatTemplate.setContent(messageConstructorHelper.createConfirmationTransferMessage());
        chatTemplateRepository.save(chatTemplate);
    }

    public void updateWorkspace(UpdateWorkspaceDto data){
        var checkWorkspace = workspaceRepository.findByWabaId(data.getWabaId());
        if(checkWorkspace != null && !checkWorkspace.getWabaId().equals(data.getWabaId())){
            throw new IllegalArgumentException("Waba sudah digunakan di workspace lain, silahkan gunakan waba yang lain.");
        }

        var waba = workspaceRepository.findWabaById(data.getWabaId());
        if(waba != null && waba.getStatus().equals("DISCONNECTED")){
            throw new IllegalArgumentException("Status waba DISCONNECTED, silahkan aktifkan terlebih dahulu atau gunakan waba yang lain.");
        }

        Workspace workspace = workspaceRepository.findById(data.getId()).get();
        workspace.setNamaWorkspace(data.getNamaWorkspace());
        workspace.setWabaId(data.getWabaId());
        workspaceRepository.save(workspace);
    }


    public Page<?> getListWorkspace(Integer page, Integer limit, String search) {
        var pageable = PageRequest.of(page - 1 , limit, Sort.by(Sort.Direction.ASC, "namaWorkspace"));
        return workspaceRepository.getWorkspaceList(search, pageable);
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

    public Page<WorkspaceAccountList> getWorkspaceAccountList(Long idWorkspace, Integer page, Integer limit){
//        var accounts = accountRepository.findAllById(workspaceRepository.findById(idWorkspace).get().getAccounts().stream().map(Account::getId).toList());
//        return accounts.stream().map(account -> new WorkspaceAccountList(account.getId(), account.getUsername(), account.getRole().name(), account.getNama())).toList();
        var pageable = PageRequest.of(page - 1 , limit, Sort.by(Sort.Direction.ASC, "nama"));
        return accountRepository.getWorkspaceAccountList(idWorkspace, pageable);

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


    public WorkspaceDashboardMatrix getDashboardMatrix (Long idWorkspace, LocalDateTime tanggalAwalOrder, LocalDateTime tanggalAkhirOrder){
        var dashboardMatrix = new WorkspaceDashboardMatrix();

        var tomorow = LocalDateTime.now().plusDays(1L);
        var sentinel = LocalDateTime.of(1970, 1,1,0,0,0);
        if (tanggalAkhirOrder != null && tanggalAkhirOrder.isAfter(LocalDateTime.now())){
            tanggalAkhirOrder = LocalDateTime.now();
        }

        dashboardMatrix.setTotalOrder(workspaceRepository.getTotalOrderByWorkspace(idWorkspace, tanggalAwalOrder, tanggalAkhirOrder, sentinel, tomorow));
        dashboardMatrix.setUnpaidOrder(workspaceRepository.getTotalUnpaidOrderByWorkspace(idWorkspace, tanggalAwalOrder, tanggalAkhirOrder, sentinel, tomorow));
        dashboardMatrix.setTotalBayar(workspaceRepository.getTotalPaidOrderByWorkspace(idWorkspace , tanggalAwalOrder, tanggalAkhirOrder, sentinel, tomorow));

        BigDecimal totalBayar = BigDecimal.valueOf(dashboardMatrix.getTotalBayar());
        BigDecimal totalOrder = BigDecimal.valueOf(dashboardMatrix.getTotalOrder());

        BigDecimal rasioBayar = BigDecimal.ZERO;

        if (totalOrder.compareTo(BigDecimal.ZERO) > 0) {
            rasioBayar = totalBayar
                    .divide(totalOrder, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));
        }

        dashboardMatrix.setRasioBayar(
                rasioBayar.setScale(2, RoundingMode.HALF_UP) + "%"
        );



        return dashboardMatrix;

    }







}
