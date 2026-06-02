package com.saktiform.api.model.Order;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class BulkUpdateStatus {
    @NotNull
    UUID id;
    @NotNull
    OrderStatus status;
}
