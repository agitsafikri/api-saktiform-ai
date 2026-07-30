package com.saktiform.api.model.label.request;

import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/** Request assign satu/banyak label ke sebuah conversation. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AssignLabelRequest {

    @NotEmpty
    private List<Long> labelIds;
}
