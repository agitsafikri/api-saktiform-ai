package com.saktiform.api.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@Entity
@Table(name = "ongkir")
public class Ongkir {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ongkir_id", nullable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "district_id", insertable = false, updatable = false)
    private District district;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "origin_city_id", insertable = false, updatable = false)
    private City originCity;



    @Column(name = "district_id")
    private Integer idDistrict;


    @Column(name = "origin_city_id")
    private Integer idOriginCity;

    @Column(name = "ongkir_value")
    private BigDecimal ongkirValue;

}