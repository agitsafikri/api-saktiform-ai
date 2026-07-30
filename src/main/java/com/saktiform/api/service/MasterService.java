package com.saktiform.api.service;

import com.saktiform.api.model.PlatformIklan;
import com.saktiform.api.repository.ProdukIklanRepository;
import com.saktiform.api.util.MediaHelper;
import io.minio.errors.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;


import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.List;

@Service
public class MasterService {

    private final ProdukIklanRepository produkIklanRepository;
    private final StorageService storageService;

    MasterService(ProdukIklanRepository produkIklanRepository,  StorageService storageService){
        this.produkIklanRepository = produkIklanRepository;
        this.storageService = storageService;
    }

    public List<String> getListFacebookPixelId (String facebookPixelId){
        return produkIklanRepository.getListProdukIklanId(PlatformIklan.FACEBOOK.name(), facebookPixelId);
    }

    public List<String> getListGoogleGtmId (String googleGtmId){
        return produkIklanRepository.getListProdukIklanId(PlatformIklan.GOOGLE.name(), googleGtmId);
    }

    public String uploadMedia(MultipartFile file){
        var path = storageService.upload(file);
       return storageService.getPublicUrl(path);
    }

    public String saveSaktiformMedia(MultipartFile file) throws ServerException, InsufficientDataException, ErrorResponseException, IOException, NoSuchAlgorithmException, InvalidKeyException, InvalidResponseException, XmlParserException, InternalException {

        var path = storageService.uploadImage(file);

        return storageService.getProdukPublicUrl(path);

    }


}
