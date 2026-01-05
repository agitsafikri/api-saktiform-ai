package com.saktiform.api.repository;

import com.saktiform.api.entity.City;
import com.saktiform.api.entity.Province;
import com.saktiform.api.model.location.CityDto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface CityRepository extends JpaRepository<City, Integer> {
    @Query("SELECT new com.saktiform.api.model.location.CityDto(id, cityName) FROM City ORDER BY cityName")
    List<CityDto> getListCity();
    @Query("SELECT new com.saktiform.api.model.location.CityDto(id, cityName) FROM City WHERE idProvince = ?1 ORDER BY cityName")
    List<CityDto> getListCityByIdProvince(Integer idProvince);


}