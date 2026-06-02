package com.saktiform.api.service;

import com.saktiform.api.entity.WhatsappBusinessApi;
import com.saktiform.api.entity.Workspace;
import com.saktiform.api.model.ErrorResponse;
import com.saktiform.api.model.whatsapp.*;
import com.saktiform.api.model.whatsapp.envelopev2.LoginPairCodeResponse;
import com.saktiform.api.repository.WhatsappBusinessApiRepository;
import com.saktiform.api.service.chat.WhatsappClientHelper;
import com.saktiform.api.util.ErrorParser;
import com.saktiform.api.util.PhoneNumberUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@Service
public class WhatsappInstanceService {
    WhatsappBusinessApiRepository whatsappBusinessApiRepository;
    WhatsappClientHelper client;

    private static DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final int START_PORT = 3100;
    private static final int END_PORT = 3199;
    private static final String BASE_DIR = "/var/lib/whatsapp";



    public WhatsappInstanceService(WhatsappBusinessApiRepository whatsappBusinessApiRepository, WhatsappClientHelper client) {
        this.whatsappBusinessApiRepository = whatsappBusinessApiRepository;
        this.client = client;
    }

    public Page<WabaListDto> getListWhatsapp (Integer page, Integer limit,String search){
        var pageable = PageRequest.of(page - 1 , limit, Sort.by(Sort.Direction.DESC, "createdAt"));
        return whatsappBusinessApiRepository.getListWaba(search, pageable);
    }

    public List<AvailableWhatsappResponse> getAvailableWhatsapp (){
        return whatsappBusinessApiRepository.getAvailableWhatsapp();
    }

    public void deleteWhatsapp (UUID wabaId){
        Workspace wabaIsUsed = whatsappBusinessApiRepository.wabaIsUsed(wabaId);
        if (wabaIsUsed != null) {
            throw new RuntimeException("Nomor whatsapp ini sedang digunakan oleh workspace "+wabaIsUsed.getNamaWorkspace());
        }
        var response = client.removeDevice(wabaId.toString());
        if (!"SUCCESS".equals(response.getCode())){
            throw new RuntimeException(response.getMessage());
        }
        whatsappBusinessApiRepository.deleteById(wabaId);

    }



    public WhatsappResponse registerWhatsappMultiDevice(RegisterWhatsappDto data) throws InterruptedException {


        var formatedPhoneNumber = PhoneNumberUtil.normalizeToIndonesianFormat(data.getNomorWhatsapp()).replace("+62", "62");

        if (whatsappBusinessApiRepository.findByNomorWhatsapp(formatedPhoneNumber) != null) {
            throw new RuntimeException("Nomor whatsapp sudah terdaftar");
        }

        WhatsappBusinessApi whatsappBusinessApi = new WhatsappBusinessApi();
        whatsappBusinessApi.setNomorWhatsapp(formatedPhoneNumber);
        whatsappBusinessApi.setCreatedAt(Instant.now());
        whatsappBusinessApi.setStatus("DISCONNECTED");

        var waba = whatsappBusinessApiRepository.save(whatsappBusinessApi);
        var newDeviceRequest = new AddNewDeviceRequest(waba.getId());
        var response = client.addNewDevice(newDeviceRequest);



        return response;




    }

    public WhatsappResponse connectMultiDevice(UUID wabaId){
        try {
            var waba = whatsappBusinessApiRepository.findById(wabaId).get();

            return client.connectMultiDevice(waba.getNomorWhatsapp(), waba.getId().toString());
        }catch (HttpClientErrorException e){
            ErrorResponse error = ErrorParser.parseError(e.getResponseBodyAsString());
            if (error != null) {
                if(error.getCode().equals("ALREADY_LOGGED_IN") ){
                    var waba = whatsappBusinessApiRepository.findById(wabaId).get();
                    waba.setStatus("CONNECTED");
                    whatsappBusinessApiRepository.save(waba);
                }
                throw new RuntimeException(error.getMessage());
            }
            throw new RuntimeException(e.getResponseBodyAsString());
        }catch (ResourceAccessException e){
            throw new RuntimeException("Whatsapp Server not running");
        }

    }
}
