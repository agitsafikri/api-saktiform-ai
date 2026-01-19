package com.saktiform.api.service;

import com.saktiform.api.entity.WhatsappBusinessApi;
import com.saktiform.api.repository.WhatsappBusinessApiRepository;
import org.springframework.stereotype.Service;

@Service
public class WhatsappBusinessService {
    private final WhatsappBusinessApiRepository whatsappBusinessApiRepository;
    public WhatsappBusinessService(WhatsappBusinessApiRepository whatsappBusinessApiRepository) {
        this.whatsappBusinessApiRepository = whatsappBusinessApiRepository;
    }

    public WhatsappBusinessApi findByPort(Integer port){
        return whatsappBusinessApiRepository.findByPort(port);
    }

    public WhatsappBusinessApi findByNomorWhatsapp(String noHp){
        return whatsappBusinessApiRepository.findByNomorWhatsapp(noHp);
    }

    public WhatsappBusinessApi findByDeviceId(String deviceId){
        return whatsappBusinessApiRepository.findByDeviceId(deviceId);
    }
}
