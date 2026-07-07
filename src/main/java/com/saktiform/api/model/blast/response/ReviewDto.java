package com.saktiform.api.model.blast.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ReviewDto {
    private String name;
    private Integer recipientCount;
    private String previewMessage;
    private Long estimatedDurationSeconds;
    private String status;
}
