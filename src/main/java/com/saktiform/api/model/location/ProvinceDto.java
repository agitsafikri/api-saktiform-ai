package com.saktiform.api.model.location;

import lombok.Value;

import java.io.Serializable;

/**
 * DTO for {@link com.saktiform.api.entity.Province}
 */
@Value
public class ProvinceDto implements Serializable {
    Integer id;
    String provinceName;
}