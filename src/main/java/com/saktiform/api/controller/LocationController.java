package com.saktiform.api.controller;



import com.saktiform.api.model.RestResponse;
import com.saktiform.api.service.LocationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;



@RestController
@RequestMapping("/location")
public class LocationController {


    private final LocationService locationService;

    public LocationController(LocationService locationService) {
        this.locationService = locationService;
    }

    @GetMapping("/province")
    public ResponseEntity<?> getProvince(){
        RestResponse restResponse = new RestResponse();
        try{
            restResponse.setSuccess(true);
            restResponse.setMessage("Success");
            restResponse.setData(locationService.getProvinces());
            return ResponseEntity.ok(restResponse);
        }catch (Exception e){
            e.printStackTrace();
            restResponse.setSuccess(false);
            restResponse.setMessage(e.getMessage());
            restResponse.setData(null);
            return ResponseEntity.badRequest().body(restResponse);
        }
    }

    @GetMapping("/city")
    public ResponseEntity<?> getCity(@RequestParam(required = false) Integer provinceId){
        RestResponse restResponse = new RestResponse();
        try{
            restResponse.setSuccess(true);
            restResponse.setMessage("Success");
            restResponse.setData(locationService.getCities(provinceId));
            return ResponseEntity.ok(restResponse);
        }catch (Exception e){
            e.printStackTrace();
            restResponse.setSuccess(false);
            restResponse.setMessage(e.getMessage());
            restResponse.setData(null);
            return ResponseEntity.badRequest().body(restResponse);
        }
    }

    @GetMapping("/district")
    public ResponseEntity<?> getDistrict(@RequestParam(required = false) Integer cityId){
        RestResponse restResponse = new RestResponse();
        try{
            restResponse.setSuccess(true);
            restResponse.setMessage("Success");
            restResponse.setData(locationService.getDistricts(cityId));
            return ResponseEntity.ok(restResponse);
        }catch (Exception e){
            e.printStackTrace();
            restResponse.setSuccess(false);
            restResponse.setMessage(e.getMessage());
            restResponse.setData(null);
            return ResponseEntity.badRequest().body(restResponse);
        }
    }





}
