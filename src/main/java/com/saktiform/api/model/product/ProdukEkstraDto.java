package com.saktiform.api.model.product;

import com.saktiform.api.entity.ProdukEkstra;
import lombok.Value;

import java.io.Serializable;
import java.util.Map;

/**
 * DTO for {@link ProdukEkstra}
 */
@Value
public class ProdukEkstraDto implements Serializable {
    String type;
    Map<String, Object> config;
}