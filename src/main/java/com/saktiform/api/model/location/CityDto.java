package com.saktiform.api.model.location;

import lombok.Value;

import java.io.Serializable;

/**
 * DTO for {@link com.saktiform.api.entity.City}
 */
@Value
public class CityDto implements Serializable {
    Integer id;
    String cityName;
}