package com.saktiform.api.validators;

import com.saktiform.api.model.product.AddProdukDto;
import com.saktiform.api.service.ProdukService;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.springframework.util.StringUtils;

public class UniqueProductNameValidator implements ConstraintValidator<UniqueProductName, AddProdukDto> {
    private ProdukService produkService;

    UniqueProductNameValidator (ProdukService produkService){
        this.produkService = produkService;
    }

    @Override
    public boolean isValid(AddProdukDto value, ConstraintValidatorContext context) {
        if(!StringUtils.hasText(value.getNamaProduk())){
            return true;
        }

        var existingProduk = produkService.findProdukByNamaProduk(value.getNamaProduk());

        if(existingProduk == null){
            return true;
        }

        if(value.getId() == null){
            return false;
        }

        return existingProduk.getId().equals(value.getId());
    }
}