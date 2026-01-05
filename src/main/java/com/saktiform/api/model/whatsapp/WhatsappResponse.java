package com.saktiform.api.model.whatsapp;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class WhatsappResponse <T>{
    private String code;
    private  String message;
    private T results;
}
