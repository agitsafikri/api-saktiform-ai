package com.saktiform.api.service;

import com.saktiform.api.model.domain.DomainDto;
import com.saktiform.api.repository.DomainRepository;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

@Service
public class DomainService {
    private final DomainRepository domainRepository;

    public DomainService(DomainRepository domainRepository) {
        this.domainRepository = domainRepository;
    }

    public Page<Object> getDomains(){
        return null;
    }

    public Object getDomainById(Long id){
        return null;
    }

    public void upsertDomain(DomainDto domain){
        var domainObj = new com.saktiform.api.entity.Domain();
        domainObj.setId(domain.getId());
        domainObj.setDomain(domain.getDomain());
        domainRepository.save(domainObj);
    }

}

