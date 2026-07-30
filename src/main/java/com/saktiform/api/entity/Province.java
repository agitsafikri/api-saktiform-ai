package com.saktiform.api.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "province")
public class Province {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "province_id", nullable = false)
    private Integer id;

    @Column(name = "province_name", length = Integer.MAX_VALUE)
    private String provinceName;

    @Column(name = "is_disabled", nullable = false)
    private Boolean isDisabled = false;

}