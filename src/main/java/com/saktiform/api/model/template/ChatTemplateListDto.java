package com.saktiform.api.model.template;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ChatTemplateListDto {
    UUID id;
    String namaTemplate;
    String kategori;
    String mediaLink;
}
