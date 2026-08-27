package com.farm2home.auth.controller;

import com.farm2home.auth.domain.enums.OtpType;
import com.farm2home.auth.dto.request.*;
import com.farm2home.auth.dto.response.AuthResponse;
import com.farm2home.auth.dto.response.GoogleAuthResponse;
import com.farm2home.auth.dto.response.OtpVerifyResponse;
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
        @DisplayName("verify-otp REGISTRATION → 200, no auth in response")
        void verifyOtp_registration_ok() throws Exception {
            VerifyOtpRequest req = new VerifyOtpRequest();
            req.setMobile("9876543210");
            req.setOtp("123456");
            req.setOtpType(OtpType.REGISTRATION);
            when(authService.verifyOtp(any(VerifyOtpRequest.class))).thenReturn(
                    OtpVerifyResponse.builder().registrationRequired(false).auth(null).build());

            mockMvc.perform(post("/api/v1/auth/verify-otp")
                            .contentType("application/json")
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.registrationRequired").value(false))
                    .andExpect(jsonPath("$.data.auth").doesNotExist());
        }

        @Test
        @DisplayName("verify-otp LOGIN, existing account → 200 with tokens")
        void verifyOtp_login_existingAccount_returnsTokens() throws Exception {
            VerifyOtpRequest req = new VerifyOtpRequest();
            req.setMobile("9876543210");
            req.setOtp("123456");
            req.setOtpType(OtpType.LOGIN);
            when(authService.verifyOtp(any(VerifyOtpRequest.class))).thenReturn(
                    OtpVerifyResponse.builder().registrationRequired(false)
                            .auth(AuthResponse.builder().accessToken("at").refreshToken("rt").build())
                            .build());

            mockMvc.perform(post("/api/v1/auth/verify-otp")
                            .contentType("application/json")
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.auth.accessToken").value("at"));
        }

        @Test
        @DisplayName("verify-otp LOGIN, no account yet → 200, registrationRequired=true, no tokens")
        void verifyOtp_login_noAccount_registrationRequired() throws Exception {
            VerifyOtpRequest req = new VerifyOtpRequest();
            req.setMobile("9876543210");
            req.setOtp("123456");
            req.setOtpType(OtpType.LOGIN);
            when(authService.verifyOtp(any(VerifyOtpRequest.class))).thenReturn(
                    OtpVerifyResponse.builder().registrationRequired(true).auth(null).build());

            mockMvc.perform(post("/api/v1/auth/verify-otp")
                            .contentType("application/json")
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.registrationRequired").value(true))
                    .andExpect(jsonPath("$.data.auth").doesNotExist());
        }

        @Test
        @DisplayName("verify-otp: OTP response body never echoes back the submitted code")
        void verifyOtp_responseNeverContainsOtp() throws Exception {
            VerifyOtpRequest req = new VerifyOtpRequest();
            req.setMobile("9876543210");
            req.setOtp("482913");
            req.setOtpType(OtpType.LOGIN);
            when(authService.verifyOtp(any(VerifyOtpRequest.class))).thenReturn(
                    OtpVerifyResponse.builder().registrationRequired(true).auth(null).build());

            String body = mockMvc.perform(post("/api/v1/auth/verify-otp")
                            .contentType("application/json")
                            .content(objectMapper.writeValueAsString(req)))
                    .andReturn().getResponse().getContentAsString();

            org.assertj.core.api.Assertions.assertThat(body).doesNotContain("482913");
        }

        @Test
        @DisplayName("invalid/expired OTP → 401 via GlobalExceptionHandler")
        void verifyOtp_invalid_unauthorized() throws Exception {
            VerifyOtpRequest req = new VerifyOtpRequest();
            req.setMobile("9876543210");
            req.setOtp("123456");
            req.setOtpType(OtpType.LOGIN);
            when(authService.verifyOtp(any(VerifyOtpRequest.class)))
                    .thenThrow(new AuthException("Invalid OTP. Please check and try again."));

            mockMvc.perform(post("/api/v1/auth/verify-otp")
                            .contentType("application/json")
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("POST /api/v1/auth/google")
    class Google {

        @Test
        @DisplayName("valid credential, existing/linked user → 200 with tokens")
        void validCredential_existingUser_returnsTokens() throws Exception {
            GoogleAuthRequest req = new GoogleAuthRequest();
            req.setCredential("valid-credential");
            when(authService.googleAuth(any(GoogleAuthRequest.class))).thenReturn(
                    GoogleAuthResponse.builder().registrationRequired(false)
                            .auth(AuthResponse.builder().accessToken("at").refreshToken("rt").build())
                            .build());

            mockMvc.perform(post("/api/v1/auth/google")
                            .contentType("application/json")
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.auth.accessToken").value("at"));
        }

        @Test
        @DisplayName("valid credential, no matching account → 200, registrationRequired=true with pre-fill")
        void validCredential_noMatch_registrationRequired() throws Exception {
            GoogleAuthRequest req = new GoogleAuthRequest();
            req.setCredential("valid-credential");
            when(authService.googleAuth(any(GoogleAuthRequest.class))).thenReturn(
                    GoogleAuthResponse.builder().registrationRequired(true)
                            .firstName("Jane").lastName("Smith").email("jane@example.com").build());

            mockMvc.perform(post("/api/v1/auth/google")
                            .contentType("application/json")
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.registrationRequired").value(true))
                    .andExpect(jsonPath("$.data.firstName").value("Jane"))
                    .andExpect(jsonPath("$.data.auth").doesNotExist());
        }

        @Test
        @DisplayName("invalid/unverifiable credential → 401 via GlobalExceptionHandler")
        void invalidCredential_unauthorized() throws Exception {
            GoogleAuthRequest req = new GoogleAuthRequest();
            req.setCredential("bogus");
            when(authService.googleAuth(any(GoogleAuthRequest.class)))
                    .thenThrow(new AuthException("Invalid or expired Google credential."));

            mockMvc.perform(post("/api/v1/auth/google")
                            .contentType("application/json")
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("blank credential → 400, service never called")
        void blankCredential_badRequest() throws Exception {
            GoogleAuthRequest req = new GoogleAuthRequest();
            req.setCredential("");

            mockMvc.perform(post("/api/v1/auth/google")
                            .contentType("application/json")
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(authService);
        }

        @Test
        @DisplayName("SECURITY: a client-supplied role field on the request is ignored - GoogleAuthRequest has no such field")
        void requestHasNoRoleField() {
            // Compile-time guarantee, not a runtime check: GoogleAuthRequest only ever exposes
            // `credential` - there is no field a client could set to request a role, admin
            // account, or any other elevated identity. See AuthServiceImpl.googleAuth(), which
            // never reads anything from the request except the credential either.
            org.assertj.core.api.Assertions.assertThat(GoogleAuthRequest.class.getDeclaredFields())
                    .extracting(java.lang.reflect.Field::getName)
                    .containsExactly("credential");
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
