package com.saktiform.api.model.domain;

import com.saktiform.api.entity.Domain;
import lombok.Value;

import java.io.Serializable;

/**
 * DTO for {@link Domain}
 */
@Value
public class DomainDto implements Serializable {
    Long id;
    String domain;
}