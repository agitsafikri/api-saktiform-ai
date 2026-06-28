package com.saktiform.api.repository;

import com.saktiform.api.entity.City;
import com.saktiform.api.entity.District;
import com.saktiform.api.model.location.DistrictDto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface DistrictRepository extends JpaRepository<District, Integer> {
    @Query("SELECT new com.saktiform.api.model.location.DistrictDto(d.id, d.districtName) FROM District d WHERE d.city.province.isDisabled = false ORDER BY d.districtName")
    List<DistrictDto> getListDistricts();

    @Query("SELECT new com.saktiform.api.model.location.DistrictDto(d.id, d.districtName) FROM District d WHERE d.idCity = :idCity AND d.city.province.isDisabled = false ORDER BY d.districtName")
    List<DistrictDto> getDistrictsByIdCity(@Param("idCity") Integer idCity);
}