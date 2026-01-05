package com.saktiform.api.validators;

import com.saktiform.api.model.product.AddProdukDto;
import com.saktiform.api.service.ProdukService;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.springframework.util.StringUtils;

public class UniqueProductUrlValidator implements ConstraintValidator<UniqueProductUrl, AddProdukDto> {
    private ProdukService produkService;

    UniqueProductUrlValidator (ProdukService produkService){
        this.produkService = produkService;
    }

    @Override
    public boolean isValid(AddProdukDto value, ConstraintValidatorContext context) {
        if(!StringUtils.hasText(value.getUrlCheckout())){
            return true;
        }

        var existingProduk = produkService.findProdukByUrlCheckout(value.getUrlCheckout());

        if(existingProduk == null){
            return true;
        }

        if(value.getId() == null){
            return false;
        }

        return existingProduk.getId().equals(value.getId());
    }
}
