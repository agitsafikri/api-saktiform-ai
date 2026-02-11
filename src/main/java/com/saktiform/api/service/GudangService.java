package com.saktiform.api.service;

import com.saktiform.api.entity.Gudang;
import com.saktiform.api.model.gudang.AddGudangDto;
import com.saktiform.api.model.gudang.GudangDetailResponse;
import com.saktiform.api.model.gudang.GudangDto;
import com.saktiform.api.repository.GudangRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
public class GudangService {
    private final GudangRepository gudangRepository;
    public GudangService(GudangRepository gudangRepository) {
        this.gudangRepository = gudangRepository;
    }
    public void upsertGudang(AddGudangDto data){
        Gudang gudang;
        if (data.getId() != null) {
            gudang = gudangRepository.findById(data.getId()).get();
            gudang.setUpdatedAt(Instant.now());
        }else {
            gudang = new Gudang();
            gudang.setCreatedAt(Instant.now());
        }

        gudang.setNamaGudang(data.getNamaGudang());
        gudang.setId(data.getId());
        gudang.setIdWorkspace(data.getIdWorkspace());
        gudang.setAlamat(data.getAlamat());
        gudang.setIdProvinsi(data.getIdProvinsi());
        gudang.setIdKota(data.getIdKota());
        gudang.setIdKecamatan(data.getIdKecamatan());
        gudang.setIsDeleted(false);

        gudangRepository.save(gudang);
    }

    public Page<GudangDto> getGudangByWorkspaceId(Long workspaceId, Integer page, Integer limit){
        var pageable = PageRequest.of(page - 1 , limit, Sort.by(Sort.Direction.DESC, "createdAt"));
        return gudangRepository.getGudangByIdWorkspace(workspaceId, pageable);
    }

    public GudangDetailResponse getDetailGudang(Long workspaceId){
        return gudangRepository.getGudangDetailById(workspaceId);
    }

    public void deleteGudangById(Long id){
        gudangRepository.deleteGudang(id);
    }
}
