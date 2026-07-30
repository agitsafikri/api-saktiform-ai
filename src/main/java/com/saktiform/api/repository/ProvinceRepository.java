package com.saktiform.api.repository;

import com.saktiform.api.entity.Province;
import com.saktiform.api.model.location.ProvinceDto;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface ProvinceRepository extends JpaRepository<Province, Integer> {

    List<Province> findByIsDisabledFalse(Sort sort);

    @Query("SELECT new com.saktiform.api.model.location.ProvinceDto(p.id, p.provinceName) FROM Province p WHERE p.isDisabled = true ORDER BY p.provinceName")
    List<ProvinceDto> findBlockedProvinces();
}