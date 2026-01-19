package com.love.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record SignupRequest(
        @NotBlank String userId,
        @NotBlank String password,
        @NotBlank String name,
        @NotNull LocalDate birthDate // "2000-01-31" 형태로 받기
) {}