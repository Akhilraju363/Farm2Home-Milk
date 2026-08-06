package com.farm2home.auth.controller;

import com.farm2home.auth.domain.entity.User;
import com.farm2home.auth.dto.request.*;
import com.farm2home.auth.dto.response.AuthResponse;
import com.farm2home.auth.service.AuthService;
import com.farm2home.common.web.dto.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * Note on the {@code @ApiResponse} annotation used below: it is always fully-qualified
 * ({@code io.swagger.v3.oas.annotations.responses.ApiResponse}) rather than imported by simple
 * name, since it collides with this codebase's own {@link ApiResponse} success envelope - the
 * same convention WalletController (payment-service) uses.
 *
 * Five of these seven endpoints (everything except logout and me) are listed in SecurityConfig's
 * PUBLIC_ENDPOINTS and require no bearer token — this is deliberate (there's no JWT to present
 * before you've logged in or registered), not an oversight, and each is documented as such below.
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Register, login, OTP verification, token management")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    @Operation(summary = "Register a new customer",
            description = "Creates a new CUSTOMER-role account (verified=false) and sends a 6-digit "
                    + "verification OTP to the given mobile number (OtpType.REGISTRATION, 5-minute expiry). "
                    + "Returns an access/refresh token pair immediately (AuthServiceImpl.register builds one the "
                    + "same way login does) so callers can proceed through post-registration steps (e.g. saving "
                    + "an address) before verification — isEnabled() is false until verify-otp is called, but "
                    + "JWT bearer validation does not check that flag. Call POST /verify-otp with the same "
                    + "mobile to activate the account. No authentication required — this is a pre-registration, "
                    + "public endpoint (listed in SecurityConfig's PUBLIC_ENDPOINTS).")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201",
                description = "Account created, OTP sent, tokens issued",
                content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                        examples = @ExampleObject(value = """
                                {
                                  "success": true,
                                  "message": "Registration successful. Please verify your mobile number with the OTP sent.",
                                  "data": {
                                    "accessToken": "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiI5ODc2NTQzMjEwIn0.abc123",
                                    "refreshToken": "8b1e6a2c-2f9a-4b8b-9c2e-0a1a2b3c4d5e.def456",
                                    "tokenType": "Bearer",
                                    "expiresIn": 604800,
                                    "user": {
                                      "id": "1a2b3c4d-5e6f-4a1b-8c9d-0e1f2a3b4c5d",
                                      "mobile": "9876543210",
                                      "email": "jane.doe@example.com",
                                      "username": "jane.doe",
                                      "verified": false,
                                      "roles": ["CUSTOMER"]
                                    }
                                  }
                                }"""))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                description = "Validation failed (e.g. invalid mobile/email format, password missing an "
                        + "uppercase/lowercase/digit/special character)", content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                description = "Mobile number or email address is already registered (AuthServiceImpl.register "
                        + "raises this as an AuthException, which this codebase maps to 401 rather than 409 — "
                        + "documented here to match actual behavior)", content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                description = "CUSTOMER role missing from the database — a server misconfiguration (seed data "
                        + "not run), not a client error", content = @Content)
    })
    public ResponseEntity<ApiResponse<AuthResponse>> register(@Valid @RequestBody RegisterRequest request) {
        AuthResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Registration successful. Please verify your mobile number with the OTP sent.", response));
    }

    @PostMapping("/login")
    @Operation(summary = "Login with mobile/email and password",
            description = "Authenticates by identifier (10-digit mobile number OR registered email address, "
                    + "see UserRepository.findByIdentifierAndDeletedFalse) + password, and returns a fresh "
                    + "access/refresh token pair. Revokes every refresh token previously issued to the user, "
                    + "so only the tokens from this login remain usable with POST /refresh-token. No "
                    + "authentication required — this is a pre-login, public endpoint (listed in "
                    + "SecurityConfig's PUBLIC_ENDPOINTS).")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                description = "Login successful",
                content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                        examples = @ExampleObject(value = """
                                {
                                  "success": true,
                                  "message": "Login successful",
                                  "data": {
                                    "accessToken": "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiI5ODc2NTQzMjEwIn0.abc123",
                                    "refreshToken": "8b1e6a2c-2f9a-4b8b-9c2e-0a1a2b3c4d5e.def456",
                                    "tokenType": "Bearer",
                                    "expiresIn": 604800,
                                    "user": {
                                      "id": "1a2b3c4d-5e6f-4a1b-8c9d-0e1f2a3b4c5d",
                                      "mobile": "9876543210",
                                      "email": "jane.doe@example.com",
                                      "username": "jane.doe",
                                      "verified": true,
                                      "roles": ["CUSTOMER"]
                                    }
                                  }
                                }"""))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                description = "Validation failed", content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                description = "Invalid mobile number/email/username or password", content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                description = "Edge case only: authentication succeeded but the user row could not be "
                        + "re-fetched immediately after (e.g. deleted concurrently)", content = @Content)
    })
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Login successful", authService.login(request)));
    }

    @PostMapping("/send-otp")
    @Operation(summary = "Send OTP to a mobile number or email's registered account",
            description = "Generates a 6-digit OTP (5-minute expiry) and sends it by SMS (best-effort — "
                    + "SmsService swallows delivery failures, so this call never fails because of an SMS "
                    + "outage), invalidating any previously unused OTP for the same combination. The "
                    + "identifier may be a 10-digit mobile number (used as-is; need not already belong to a "
                    + "registered user, since REGISTRATION OTPs are requested before the account exists) or "
                    + "an email address (resolved to that account's mobile number - if the email matches no "
                    + "account, this silently does nothing rather than ever confirming/denying it's "
                    + "registered). Also emails the OTP if the resolved user has an email on file. No "
                    + "authentication required — this is a pre-login, public endpoint (listed in "
                    + "SecurityConfig's PUBLIC_ENDPOINTS).")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                description = "Always returns success, whether or not an OTP was actually generated/sent - "
                        + "see the identifier resolution rules above"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                description = "Validation failed (blank identifier or missing otpType)", content = @Content)
    })
    public ResponseEntity<ApiResponse<Void>> sendOtp(@Valid @RequestBody OtpRequest request) {
        authService.sendOtp(request);
        return ResponseEntity.ok(ApiResponse.success(
                "If " + request.getIdentifier() + " is registered, an OTP has been sent.", null));
    }

    @PostMapping("/verify-otp")
    @Operation(summary = "Verify OTP",
            description = "Validates the OTP against the most recently generated, still-unused record for the "
                    + "given mobile + otpType. When otpType=REGISTRATION, also marks the matching user account "
                    + "verified=true. No authentication required — this is a pre-login, public endpoint "
                    + "(listed in SecurityConfig's PUBLIC_ENDPOINTS).")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                description = "OTP verified"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                description = "Validation failed (OTP not exactly 6 digits, or invalid mobile format)",
                content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                description = "No (unused) OTP exists for this mobile + otpType, it has expired, or it does "
                        + "not match", content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                description = "Edge case only: otpType=REGISTRATION but no user exists for the mobile number",
                content = @Content)
    })
    public ResponseEntity<ApiResponse<Void>> verifyOtp(@Valid @RequestBody VerifyOtpRequest request) {
        authService.verifyOtp(request);
        return ResponseEntity.ok(ApiResponse.success("OTP verified successfully.", null));
    }

    @PostMapping("/refresh-token")
    @Operation(summary = "Get new access token using refresh token",
            description = "Exchanges a still-valid, unrevoked refresh token for a brand-new access/refresh "
                    + "token pair. The presented refresh token is revoked as part of the same call — refresh "
                    + "tokens are single-use and cannot be redeemed twice. No authentication required via "
                    + "bearer token — the refresh token itself is the credential (this is a pre-login, public "
                    + "endpoint, listed in SecurityConfig's PUBLIC_ENDPOINTS).")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                description = "Token refreshed"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                description = "Validation failed (blank refreshToken)", content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                description = "Refresh token is unknown/already revoked, or has expired", content = @Content)
    })
    public ResponseEntity<ApiResponse<AuthResponse>> refreshToken(@Valid @RequestBody RefreshTokenRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Token refreshed successfully", authService.refreshToken(request)));
    }

    @PostMapping("/logout")
    @Operation(summary = "Logout and revoke tokens",
            description = "Revokes every refresh token issued to the caller (resolved from the bearer access "
                    + "token's mobile claim), so none of them can be redeemed via POST /refresh-token "
                    + "afterward. The access token itself is stateless and is not blacklisted — it keeps "
                    + "authenticating requests until it naturally expires.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                description = "Logged out — all refresh tokens for the user revoked"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                description = "Missing/invalid Authorization header, or the bearer token's mobile claim does "
                        + "not match an existing user", content = @Content)
    })
    public ResponseEntity<ApiResponse<Void>> logout(
            @RequestHeader("Authorization") String authHeader) {
        authService.logout(authHeader);
        return ResponseEntity.ok(ApiResponse.success("Logged out successfully.", null));
    }

    @GetMapping("/me")
    @Operation(summary = "Get the current authenticated user's profile",
            description = "Returns the profile (username, mobile, email, roles, verified) of whichever user "
                    + "the bearer token belongs to. Exists because the frontend only persists the token "
                    + "itself, not the user object built from it - this lets it repopulate that object on "
                    + "app boot/page refresh instead of showing a blank/unknown user until the next login.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                description = "Profile retrieved"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                description = "Missing or invalid bearer token", content = @Content)
    })
    public ResponseEntity<ApiResponse<AuthResponse.UserInfo>> me(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(ApiResponse.success("Profile retrieved successfully", authService.getCurrentUserInfo(user)));
    }
}
