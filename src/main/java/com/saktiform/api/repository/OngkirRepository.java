package com.saktiform.api.repository;

import com.saktiform.api.entity.Ongkir;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OngkirRepository extends JpaRepository<Ongkir, Long> {
    Ongkir findByIdOriginCityAndIdDistrict(Integer idOriginCity, Integer idDistrict);
}