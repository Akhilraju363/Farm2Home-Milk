package com.farm2home.farm.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

@Data
public class CreateCowRequest {

    @NotBlank(message = "Tag number is required")
    @Size(max = 20, message = "Tag number must be at most 20 characters")
    private String tagNumber;

    @Size(max = 100, message = "Cow name must be at most 100 characters")
    private String cowName;

    @NotBlank(message = "Breed is required")
    @Size(max = 100, message = "Breed must be at most 100 characters")
    private String breed;

    @PastOrPresent(message = "Date of birth cannot be in the future")
    private LocalDate dateOfBirth;

    @PastOrPresent(message = "Purchase date cannot be in the future")
    private LocalDate purchaseDate;
}
