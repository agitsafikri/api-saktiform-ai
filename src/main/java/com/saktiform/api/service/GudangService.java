package com.saktiform.api.service;

import com.saktiform.api.entity.Gudang;
import com.saktiform.api.model.gudang.AddGudangDto;
import com.saktiform.api.model.gudang.GudangDto;
import com.saktiform.api.repository.GudangRepository;
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
        gudang.setIdProvinsi(data.getProvinsi());
        gudang.setIdKota(data.getKota());
        gudang.setIdKecamatan(data.getKecamatan());

        gudangRepository.save(gudang);
    }

    public List<GudangDto> getGudangByWorkspaceId(Long workspaceId){
        return gudangRepository.getGudangByIdWorkspace(workspaceId);
    }

    public void deleteGudangById(Long id){
        gudangRepository.deleteGudang(id);
    }
}
