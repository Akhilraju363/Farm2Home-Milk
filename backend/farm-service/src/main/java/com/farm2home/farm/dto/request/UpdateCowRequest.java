package com.farm2home.farm.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

@Data
public class UpdateCowRequest {

    @Size(max = 100, message = "Cow name must be at most 100 characters")
    private String cowName;

    @Size(max = 100, message = "Breed must be at most 100 characters")
    private String breed;
    private LocalDate dateOfBirth;
    private LocalDate purchaseDate;
}
