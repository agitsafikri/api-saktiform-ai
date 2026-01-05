package com.saktiform.api.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saktiform.api.model.ErrorResponse;

public class ErrorParser {

    private static final ObjectMapper mapper = new ObjectMapper();

    public static ErrorResponse parseError(String errorMessage) {
        try {
            // cari posisi JSON (dimulai dari '{')
            int start = errorMessage.indexOf("{");
            int end = errorMessage.lastIndexOf("}") + 1;

            if (start >= 0 && end > start) {
                String json = errorMessage.substring(start, end);
                return mapper.readValue(json, ErrorResponse.class);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null; // kalau gagal parsing
    }
}
