package com.saktiform.api.service;

import com.saktiform.api.entity.Domain;
import com.saktiform.api.model.domain.UpsertDomainPayload;
import com.saktiform.api.model.domain.DomainDto;
import com.saktiform.api.repository.DomainRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
public class DomainService {
    private final DomainRepository domainRepository;

    public DomainService(DomainRepository domainRepository) {
        this.domainRepository = domainRepository;
    }

    public Page<Object> getDomains(){
        return null;
    }

    public Domain getDomainById(Long id){
        return domainRepository.findById(id).get();
    }

    public void upsertDomain(UpsertDomainPayload domain){
        var domainObj = new Domain();
        domainObj.setId(domain.getId());
        domainObj.setDomain(domain.getDomain());
        domainObj.setWorkspaceId(domain.getWorkspaceId());
        domainObj.setCreatedAt(Instant.now());
        domainRepository.save(domainObj);
    }

    public Page<DomainDto> getListDomain(Long workspaceId, Integer page, Integer limit ){
        var pageable = PageRequest.of(page - 1 , limit, Sort.by(Sort.Direction.ASC, "createdAt"));
        return  domainRepository.getDomainList(workspaceId, pageable);
    }

    public List<DomainDto> getDomainDropDown(Long workspaceId){
        return domainRepository.getDomainByWorkspaceId(workspaceId);
    }

    public void deleteDomain(Long id) {
        try {
            domainRepository.deleteById(id);
        } catch (DataIntegrityViolationException e) {
            throw new RuntimeException(
                    "Domain tidak bisa dihapus karena masih digunakan oleh workspace"
            );
        }
    }





}

