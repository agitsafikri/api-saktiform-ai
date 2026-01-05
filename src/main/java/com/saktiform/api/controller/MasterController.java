package com.saktiform.api.controller;

import com.saktiform.api.model.RestResponse;
import com.saktiform.api.service.MasterService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/master")
public class MasterController {

    MasterService masterService;

    MasterController(MasterService masterService){
        this.masterService = masterService;
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

}
