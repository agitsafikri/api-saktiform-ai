package com.saktiform.api.model.blast.request;

import com.saktiform.api.model.blast.enums.MessageSource;
import com.saktiform.api.model.blast.enums.TargetType;
import jakarta.validation.constraints.NotBlank;
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
public class CreateCampaignRequest {

    @NotNull
    private Long importId;

    @NotBlank
    private String name;

    @NotNull
    private TargetType targetType;

    @NotNull
    private MessageSource messageSource;

    private UUID templateId;   // wajib jika messageSource = TEMPLATE (divalidasi service)

    private String content;    // wajib jika messageSource = CUSTOM

    private String mediaLink;

    private String deviceId;   // opsional; default WABA workspace

    private CampaignConfig config;
}
