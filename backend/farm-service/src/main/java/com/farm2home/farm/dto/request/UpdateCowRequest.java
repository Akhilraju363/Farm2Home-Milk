package com.farm2home.farm.dto.request;

import lombok.Data;

import java.time.LocalDate;

@Data
public class UpdateCowRequest {
    private String cowName;
    private String breed;
    private LocalDate dateOfBirth;
    private LocalDate purchaseDate;
}
