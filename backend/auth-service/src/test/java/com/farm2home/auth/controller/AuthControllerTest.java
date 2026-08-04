package com.farm2home.auth.controller;

import com.farm2home.auth.domain.enums.OtpType;
import com.farm2home.auth.dto.request.*;
import com.farm2home.auth.dto.response.AuthResponse;
import com.farm2home.auth.exception.AuthException;
import com.farm2home.auth.service.AuthService;
import com.farm2home.auth.service.JwtService;
import com.farm2home.auth.service.UserDetailsServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockBean private AuthService authService;
    // @WebMvcTest still constructs Filter-type @Component beans (JwtAuthenticationFilter,
    // scanned automatically) even with addFilters=false, so its own dependency must be
    // satisfiable in the slice context.
    @MockBean private JwtService jwtService;
    @MockBean private UserDetailsServiceImpl userDetailsService;

    @Nested
    @DisplayName("POST /api/v1/auth/register")
    class Register {

        @Test
        @DisplayName("valid request → 201, delegates to service")
        void validRequest_created() throws Exception {
            RegisterRequest req = new RegisterRequest();
            req.setFirstName("John");
            req.setLastName("Doe");
            req.setMobile("9876543210");
            req.setPassword("Passw0rd!");

            mockMvc.perform(post("/api/v1/auth/register")
                            .contentType("application/json")
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.success").value(true));

            verify(authService).register(any(RegisterRequest.class));
        }

        @Test
        @DisplayName("invalid mobile number → 400, service never called")
        void invalidMobile_badRequest() throws Exception {
            RegisterRequest req = new RegisterRequest();
            req.setFirstName("John");
            req.setLastName("Doe");
            req.setMobile("123"); // fails pattern
            req.setPassword("Passw0rd!");

            mockMvc.perform(post("/api/v1/auth/register")
                            .contentType("application/json")
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(authService);
        }
    }

    @Nested
    @DisplayName("POST /api/v1/auth/login")
    class Login {

        @Test
        @DisplayName("valid credentials → 200 with tokens")
        void validCredentials_ok() throws Exception {
            LoginRequest req = new LoginRequest();
            req.setIdentifier("9876543210");
            req.setPassword("Passw0rd!");

            when(authService.login(any(LoginRequest.class))).thenReturn(
                    AuthResponse.builder().accessToken("at").refreshToken("rt").build());

            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType("application/json")
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.accessToken").value("at"));
        }

        @Test
        @DisplayName("bad credentials → 401 via GlobalExceptionHandler")
        void badCredentials_unauthorized() throws Exception {
            LoginRequest req = new LoginRequest();
            req.setIdentifier("9876543210");
            req.setPassword("wrong");

            when(authService.login(any(LoginRequest.class)))
                    .thenThrow(new AuthException("Invalid mobile number or password."));

            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType("application/json")
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("POST /api/v1/auth/send-otp and /verify-otp")
    class Otp {

        @Test
        @DisplayName("send-otp → 200, delegates")
        void sendOtp_ok() throws Exception {
            OtpRequest req = new OtpRequest();
            req.setIdentifier("9876543210");
            req.setOtpType(OtpType.LOGIN);

            mockMvc.perform(post("/api/v1/auth/send-otp")
                            .contentType("application/json")
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk());

            verify(authService).sendOtp(any(OtpRequest.class));
        }

        @Test
        @DisplayName("verify-otp → 200, delegates")
        void verifyOtp_ok() throws Exception {
            VerifyOtpRequest req = new VerifyOtpRequest();
            req.setMobile("9876543210");
            req.setOtp("123456");
            req.setOtpType(OtpType.REGISTRATION);

            mockMvc.perform(post("/api/v1/auth/verify-otp")
                            .contentType("application/json")
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk());

            verify(authService).verifyOtp(any(VerifyOtpRequest.class));
        }
    }

    @Nested
    @DisplayName("POST /api/v1/auth/refresh-token and /logout")
    class RefreshAndLogout {

        @Test
        @DisplayName("refresh-token → 200 with new tokens")
        void refreshToken_ok() throws Exception {
            RefreshTokenRequest req = new RefreshTokenRequest();
            req.setRefreshToken("some-token");

            when(authService.refreshToken(any(RefreshTokenRequest.class))).thenReturn(
                    AuthResponse.builder().accessToken("new-at").build());

            mockMvc.perform(post("/api/v1/auth/refresh-token")
                            .contentType("application/json")
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.accessToken").value("new-at"));
        }

        @Test
        @DisplayName("logout → 200, passes Authorization header through")
        void logout_ok() throws Exception {
            mockMvc.perform(post("/api/v1/auth/logout")
                            .header("Authorization", "Bearer sometoken"))
                    .andExpect(status().isOk());

            verify(authService).logout(eq("Bearer sometoken"));
        }
    }
}
