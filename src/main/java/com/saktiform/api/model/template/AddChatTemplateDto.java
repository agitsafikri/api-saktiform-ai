package com.saktiform.api.model.template;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AddChatTemplateDto {
    UUID id;
    Long idWorkspace;
    String namaTemplate;
    String content;
    String mediaLink;
}
