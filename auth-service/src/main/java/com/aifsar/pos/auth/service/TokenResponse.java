package com.aifsar.pos.auth.service;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record TokenResponse(String accessToken, String tokenType, long expiresIn, String refreshToken,
                            List<String> roles) {
}
