package com.saktiform.api.controller;

import com.saktiform.api.model.RestResponse;
import com.saktiform.api.model.master.SetAiKeyPayload;
import com.saktiform.api.service.AppConfigService;
import com.saktiform.api.service.LocationService;
import com.saktiform.api.service.MasterService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/master")
public class MasterController {

    private final MasterService masterService;
    private final AppConfigService appConfigService;
    private final LocationService locationService;

    MasterController(MasterService masterService, AppConfigService appConfigService, LocationService locationService){
        this.masterService = masterService;
        this.appConfigService = appConfigService;
        this.locationService = locationService;
    }
    @GetMapping("/facebook-pixel")
    public ResponseEntity<?> getListFacebookPixel(@RequestParam String facebookPixelId) {
        RestResponse rest = new RestResponse();
        try {
            var data = masterService.getListFacebookPixelId(facebookPixelId);
            rest.setSuccess(true);
            rest.setData(data);
            rest.setMessage("Success");
            return ResponseEntity.ok(rest);
        } catch (Exception e) {
            e.printStackTrace();
            rest.setSuccess(false);
            rest.setMessage(e.getMessage());
            rest.setData(null);
            return ResponseEntity.badRequest().body(rest);
        }
    }

    @GetMapping("/google-gtm")
    public ResponseEntity<?> getListGoogleGtm(@RequestParam String googleGtmId) {
        RestResponse rest = new RestResponse();
        try {
            var data = masterService.getListGoogleGtmId(googleGtmId);
            rest.setData(data);
            rest.setSuccess(true);
            rest.setMessage("Success");
            return ResponseEntity.ok(rest);
        } catch (Exception e) {
            e.printStackTrace();
            rest.setSuccess(false);
            rest.setMessage(e.getMessage());
            rest.setData(null);
            return ResponseEntity.badRequest().body(rest);
        }
    }



    @PostMapping(value = ("/upload-file"), consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadFile(@RequestParam("file") MultipartFile file) {
        RestResponse response = new RestResponse();
        try {
            String url = masterService.uploadMedia(file);

            if (url.toLowerCase().equals("failed")) {
                throw new Exception("Failed to upload file");
            }
            response.setSuccess(true);
            response.setData(url);
            response.setMessage("Upload success");
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            response.setSuccess(false);
            response.setMessage(e.getMessage());
            response.setData(null);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    @PostMapping("/ai-key")
    public ResponseEntity<?> setAiKey(@RequestBody SetAiKeyPayload key) {
        RestResponse response = new RestResponse();
        try{

            response.setSuccess(true);
            response.setMessage("Success");
            response.setData(appConfigService.saveConfig("AI_KEY", key.getKey()));
            return ResponseEntity.ok(response);
        }catch (Exception e){
            e.printStackTrace();
            response.setSuccess(false);
            response.setMessage(e.getMessage());
            response.setData(null);
            return ResponseEntity.badRequest().body(response);
        }
    }

    @GetMapping("/province/blocked")
    public ResponseEntity<?> getBlockedProvinces(){
        RestResponse restResponse = new RestResponse();
        try{
            restResponse.setSuccess(true);
            restResponse.setMessage("Success");
            restResponse.setData(locationService.getBlockedProvinces());
            return ResponseEntity.ok(restResponse);
        }catch (Exception e){
            e.printStackTrace();
            restResponse.setSuccess(false);
            restResponse.setMessage(e.getMessage());
            restResponse.setData(null);
            return ResponseEntity.badRequest().body(restResponse);
        }
    }

    @PostMapping("/province/block")
    public ResponseEntity<?> blockProvinces(@RequestBody List<Integer> provinceIds){
        RestResponse restResponse = new RestResponse();
        try{
            locationService.setProvincesDisabled(provinceIds, true);
            restResponse.setSuccess(true);
            restResponse.setMessage("Success");
            restResponse.setData(null);
            return ResponseEntity.ok(restResponse);
        }catch (NoSuchElementException e){
            restResponse.setSuccess(false);
            restResponse.setMessage(e.getMessage());
            restResponse.setData(null);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(restResponse);
        }catch (Exception e){
            e.printStackTrace();
            restResponse.setSuccess(false);
            restResponse.setMessage(e.getMessage());
            restResponse.setData(null);
            return ResponseEntity.badRequest().body(restResponse);
        }
    }

    @PostMapping("/province/unblock")
    public ResponseEntity<?> unblockProvinces(@RequestBody List<Integer> provinceIds){
        RestResponse restResponse = new RestResponse();
        try{
            locationService.setProvincesDisabled(provinceIds, false);
            restResponse.setSuccess(true);
            restResponse.setMessage("Success");
            restResponse.setData(null);
            return ResponseEntity.ok(restResponse);
        }catch (NoSuchElementException e){
            restResponse.setSuccess(false);
            restResponse.setMessage(e.getMessage());
            restResponse.setData(null);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(restResponse);
        }catch (Exception e){
            e.printStackTrace();
            restResponse.setSuccess(false);
            restResponse.setMessage(e.getMessage());
            restResponse.setData(null);
            return ResponseEntity.badRequest().body(restResponse);
        }
    }

    @GetMapping("/ai-key")
    public ResponseEntity<?> getAiKey() {
        RestResponse response = new RestResponse();
        try{

            response.setSuccess(true);
            response.setMessage("Success");
            response.setData( appConfigService.getConfig("AI_KEY" ));
            return ResponseEntity.ok(response);
        }catch (Exception e){
            e.printStackTrace();
            response.setSuccess(false);
            response.setMessage(e.getMessage());
            response.setData(null);
            return ResponseEntity.badRequest().body(response);
        }
    }

}
