package com.saktiform.api.service;

import com.saktiform.api.model.PlatformIklan;
import com.saktiform.api.repository.ProdukIklanRepository;
import com.saktiform.api.util.MediaHelper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;


import java.util.List;

@Service
public class MasterService {
    @Value("${media.base.directory}")
    private String mediaDirectory;

    MediaHelper mediaHelper;
    ProdukIklanRepository produkIklanRepository;

    MasterService(ProdukIklanRepository produkIklanRepository, MediaHelper mediaHelper){
        this.produkIklanRepository = produkIklanRepository;
        this.mediaHelper = mediaHelper;
    }

    public List<String> getListFacebookPixelId (String facebookPixelId){
        return produkIklanRepository.getListProdukIklanId(PlatformIklan.FACEBOOK.name(), facebookPixelId);
    }

    public List<String> getListGoogleGtmId (String googleGtmId){
        return produkIklanRepository.getListProdukIklanId(PlatformIklan.GOOGLE.name(), googleGtmId);
    }

    public String uploadMedia(MultipartFile file){
       return mediaHelper.saveFile(file);
    }


}
