package com.saktiform.api.repository;

import com.saktiform.api.entity.City;
import com.saktiform.api.entity.Province;
import com.saktiform.api.model.location.CityDto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface CityRepository extends JpaRepository<City, Integer> {
    @Query("SELECT new com.saktiform.api.model.location.CityDto(c.id, c.cityName) FROM City c WHERE c.province.isDisabled = false ORDER BY c.cityName")
    List<CityDto> getListCity();
    @Query("SELECT new com.saktiform.api.model.location.CityDto(c.id, c.cityName) FROM City c WHERE c.idProvince = ?1 AND c.province.isDisabled = false ORDER BY c.cityName")
    List<CityDto> getListCityByIdProvince(Integer idProvince);


}