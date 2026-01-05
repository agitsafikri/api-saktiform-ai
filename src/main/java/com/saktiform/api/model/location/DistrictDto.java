package com.saktiform.api.model.location;

import lombok.Value;

import java.io.Serializable;

/**
 * DTO for {@link com.saktiform.api.entity.District}
 */
@Value
public class DistrictDto implements Serializable {
    Integer id;
    String districtName;
}