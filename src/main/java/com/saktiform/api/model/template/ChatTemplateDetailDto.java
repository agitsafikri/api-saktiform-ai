package com.saktiform.api.model.template;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@AllArgsConstructor
public class ChatTemplateDetailDto {
    UUID id;
    String namaTemplate;
    String content;
}
