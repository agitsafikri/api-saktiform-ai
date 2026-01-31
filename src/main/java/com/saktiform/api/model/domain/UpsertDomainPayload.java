package com.saktiform.api.model.domain;

import com.saktiform.api.entity.Domain;
import lombok.Value;

import java.io.Serializable;

/**
 * DTO for {@link Domain}
 */
@Value
public class UpsertDomainPayload implements Serializable {
    Long id;
    Long workspaceId;
    String domain;
}