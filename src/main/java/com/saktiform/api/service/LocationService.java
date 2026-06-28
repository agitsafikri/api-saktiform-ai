package com.saktiform.api.service;

import com.saktiform.api.entity.City;
import com.saktiform.api.entity.Province;
import com.saktiform.api.model.location.CityDto;
import com.saktiform.api.model.location.DistrictDto;
import com.saktiform.api.repository.CityRepository;
import com.saktiform.api.repository.DistrictRepository;
import com.saktiform.api.repository.ProvinceRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.stream.Collectors;

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
        return provinceRepository.findByIsDisabledFalse(Sort.by(Sort.Direction.ASC, "provinceName"));
    }

    public List<Province> getBlockedProvinces(){
        return provinceRepository.findByIsDisabledTrue(Sort.by(Sort.Direction.ASC, "provinceName"));
    }

    public void setProvincesDisabled(List<Integer> provinceIds, boolean disabled){
        List<Province> provinces = provinceRepository.findAllById(provinceIds);
        Set<Integer> foundIds = provinces.stream().map(Province::getId).collect(Collectors.toSet());
        List<Integer> missing = provinceIds.stream().filter(id -> !foundIds.contains(id)).distinct().toList();
        if(!missing.isEmpty()){
            throw new NoSuchElementException("Province not found: " + missing);
        }
        provinces.forEach(p -> p.setIsDisabled(disabled));
        provinceRepository.saveAll(provinces);
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
