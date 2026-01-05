package com.saktiform.api.service;

import com.saktiform.api.entity.City;
import com.saktiform.api.entity.Province;
import com.saktiform.api.model.location.CityDto;
import com.saktiform.api.model.location.DistrictDto;
import com.saktiform.api.repository.CityRepository;
import com.saktiform.api.repository.DistrictRepository;
import com.saktiform.api.repository.ProvinceRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class LocationService {
    private final ProvinceRepository provinceRepository;
    private final CityRepository cityRepository;
    private final DistrictRepository districtRepository;

    LocationService(ProvinceRepository provinceRepository, CityRepository cityRepository, DistrictRepository districtRepository) {
        this.provinceRepository = provinceRepository;
        this.cityRepository = cityRepository;
        this.districtRepository = districtRepository;
    }

    public List<Province> getProvinces(){
        return provinceRepository.findAll();
    }

    public List<CityDto> getCities(Integer idProvince){
        if(idProvince == null) return cityRepository.getListCity();
        return cityRepository.getListCityByIdProvince(idProvince);
    }

    public List<DistrictDto> getDistricts(Integer idCity){
        if (idCity == null) return districtRepository.getListDistricts();
        var districts = districtRepository.getDistrictsByIdCity(idCity);
        return districts.isEmpty() ? new ArrayList<>() : districts;
    }
}
