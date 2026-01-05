package com.saktiform.api.model;

import lombok.Data;

import java.io.Serializable;

@Data
public class RestResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    //	private int status = 200;
    private boolean success = true;
    private String message = "success";
    private Object data;

    public RestResponse(){
    }

    public RestResponse(String message, Object data) {
        this.data = data;
        this.message = message;
    }

    public RestResponse(boolean success, String message){
        this.success = success;
        this.message = message;
    }

    public RestResponse(boolean success, String message, Object data) {
        this.success = success;
        this.message = message;
        this.data = data;
    }
}
