package com.navalrivals.infra.exception.dto;

import org.springframework.validation.FieldError;

public record ErrorValidationResponse(

        String field,
        String message
) {
    public ErrorValidationResponse(FieldError error){
        this(error.getField(), error.getDefaultMessage());
    }
}
