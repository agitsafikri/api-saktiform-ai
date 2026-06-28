package com.saktiform.api.repository;

import com.saktiform.api.entity.Province;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProvinceRepository extends JpaRepository<Province, Integer> {

    List<Province> findByIsDisabledFalse(Sort sort);

    List<Province> findByIsDisabledTrue(Sort sort);
}