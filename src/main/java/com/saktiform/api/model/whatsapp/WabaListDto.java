package com.saktiform.api.model.whatsapp;

import com.saktiform.api.entity.WhatsappBusinessApi;
import lombok.*;

import java.io.Serializable;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class WabaListDto  {
    UUID id;
    String nomorWhatsapp;
    String status;
    String workspace;
}