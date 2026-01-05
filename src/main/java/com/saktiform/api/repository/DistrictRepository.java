package com.saktiform.api.repository;

import com.saktiform.api.entity.City;
import com.saktiform.api.entity.District;
import com.saktiform.api.model.location.DistrictDto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface DistrictRepository extends JpaRepository<District, Integer> {
    @Query("SELECT new com.saktiform.api.model.location.DistrictDto(id, districtName) FROM District ORDER BY districtName")
    List<DistrictDto> getListDistricts();

    @Query("SELECT new com.saktiform.api.model.location.DistrictDto(id, districtName) FROM District WHERE idCity = :idCity ORDER BY districtName")
    List<DistrictDto> getDistrictsByIdCity(@Param("idCity") Integer idCity);
}