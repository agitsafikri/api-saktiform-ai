package com.saktiform.api.util;

import com.saktiform.api.model.ErrorDto;
import org.springframework.validation.ObjectError;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

public class MapperHelper {

    private static <T> Object getFieldValue(Object object, String fieldName){
        try{
            Field field = object.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            var value = field.get(object);
            return value;
        }catch (Exception exception){
        }
        return null;
    }

    public static <T> String getStringField(T object, String fieldName){
        try{
            return getFieldValue(object, fieldName).toString();
        }catch (Exception exception){
            return null;
        }
    }

    public static List<ErrorDto> getErrors(List<ObjectError> errors){
        var dto = new ArrayList<ErrorDto>();
        for(var error : errors){
            var fieldName = getStringField(error.getArguments()[0], "defaultMessage");
            fieldName = (fieldName.equals("")) ? "object" : fieldName;
            dto.add(new ErrorDto(fieldName, error.getDefaultMessage()));
        }
        return dto;
    }
}