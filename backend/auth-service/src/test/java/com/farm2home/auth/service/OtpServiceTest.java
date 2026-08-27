package com.farm2home.auth.service;

import com.farm2home.auth.config.OtpProperties;
import com.farm2home.auth.domain.entity.OtpVerification;
import com.farm2home.auth.domain.entity.User;
import com.farm2home.auth.domain.enums.OtpType;
import com.farm2home.auth.domain.repository.OtpVerificationRepository;
import com.farm2home.auth.domain.repository.UserRepository;
import com.farm2home.auth.exception.AuthException;
import com.farm2home.auth.kafka.OtpEventProducer;
import com.farm2home.common.core.audit.AuditLogService;
import com.farm2home.common.core.constants.EmailTemplateConstants;
import com.farm2home.common.core.sms.SmsSendResult;
import com.farm2home.common.core.sms.SmsService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class OtpServiceTest {

    // Low cost factor (4, vs SecurityConfig's real 12) keeps hashing fast in tests while still
    // exercising the real BCrypt encode/matches contract - not mocked, since the whole point of
    // several tests below is proving the OTP is actually hashed, not just that some string is
    // stored.
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);

    private OtpProperties defaultProperties() {
        OtpProperties properties = new OtpProperties();
        properties.setExpirySeconds(300);
        properties.setMaxAttempts(5);
        properties.setResendCooldownSeconds(30);
        properties.setMaxResends(3);
        return properties;
    }

    private OtpService newService(OtpVerificationRepository repo, UserRepository userRepository,
                                   OtpEventProducer otpEventProducer, AuditLogService auditLogService,
                                   SmsService smsService, OtpProperties properties) {
        return new OtpService(repo, userRepository, otpEventProducer, auditLogService, smsService, passwordEncoder, properties);
    }

    @Nested
    @DisplayName("generateAndSend()")
    class GenerateAndSend {

        @Test
        @DisplayName("invalidates previous OTPs and saves a new one, hashed (never the raw digits)")
        void savesNewHashedOtp() {
            OtpVerificationRepository repo = mock(OtpVerificationRepository.class);
            UserRepository userRepository = mock(UserRepository.class);
            OtpEventProducer otpEventProducer = mock(OtpEventProducer.class);
            AuditLogService auditLogService = mock(AuditLogService.class);
            SmsService smsService = mock(SmsService.class);
            when(userRepository.findByMobileAndDeletedFalse(any())).thenReturn(Optional.empty());
            OtpService svc = newService(repo, userRepository, otpEventProducer, auditLogService, smsService, defaultProperties());

            svc.generateAndSend("9876543210", OtpType.LOGIN);

            verify(repo).markAllUsedByMobileAndType("9876543210", OtpType.LOGIN);
            verify(auditLogService).record(argThat(entry ->
                    entry.getAction().equals(com.farm2home.common.core.audit.AuditAction.OTP_SENT)
                            && "9876543210".equals(entry.getUsername())));
            // The SMS body still carries the real 6-digit code (that's how the customer receives
            // it) - only the persisted record is hashed.
            verify(smsService).sendSms(eq("9876543210"), contains("OTP"), eq(EmailTemplateConstants.EVENT_OTP));

            ArgumentCaptor<OtpVerification> captor = ArgumentCaptor.forClass(OtpVerification.class);
            verify(repo).save(captor.capture());
            OtpVerification saved = captor.getValue();
            assertThat(saved.getMobile()).isEqualTo("9876543210");
            assertThat(saved.getOtpType()).isEqualTo(OtpType.LOGIN);
            assertThat(saved.getOtp()).doesNotMatch("\\d{6}"); // never the raw digits
            assertThat(saved.getOtp()).startsWith("$2"); // BCrypt hash prefix
            assertThat(saved.isUsed()).isFalse();
            assertThat(saved.getAttempts()).isZero();
            assertThat(saved.getExpiresAt()).isAfter(LocalDateTime.now());
            verify(otpEventProducer, never()).publishOtpGenerated(any(), any());
        }

        @Test
        @DisplayName("matching user with an email on file → also publishes an OTP email event, with the raw code")
        void userWithEmail_publishesEvent() {
            OtpVerificationRepository repo = mock(OtpVerificationRepository.class);
            UserRepository userRepository = mock(UserRepository.class);
            OtpEventProducer otpEventProducer = mock(OtpEventProducer.class);
            User user = User.builder().id(UUID.randomUUID()).mobile("9876543210")
                    .email("test@example.com").username("test.user").build();
            when(userRepository.findByMobileAndDeletedFalse("9876543210")).thenReturn(Optional.of(user));
            OtpService svc = newService(repo, userRepository, otpEventProducer, mock(AuditLogService.class), mock(SmsService.class), defaultProperties());

            svc.generateAndSend("9876543210", OtpType.LOGIN);

            ArgumentCaptor<String> otpCaptor = ArgumentCaptor.forClass(String.class);
            verify(otpEventProducer).publishOtpGenerated(eq(user), otpCaptor.capture());
            assertThat(otpCaptor.getValue()).matches("\\d{6}");
        }

        @Test
        @DisplayName("matching user with no email on file → no email event published")
        void userWithoutEmail_noEvent() {
            OtpVerificationRepository repo = mock(OtpVerificationRepository.class);
            UserRepository userRepository = mock(UserRepository.class);
            OtpEventProducer otpEventProducer = mock(OtpEventProducer.class);
            User user = User.builder().id(UUID.randomUUID()).mobile("9876543210")
                    .email(null).username("test.user").build();
            when(userRepository.findByMobileAndDeletedFalse("9876543210")).thenReturn(Optional.of(user));
            OtpService svc = newService(repo, userRepository, otpEventProducer, mock(AuditLogService.class), mock(SmsService.class), defaultProperties());

            svc.generateAndSend("9876543210", OtpType.LOGIN);

            verify(otpEventProducer, never()).publishOtpGenerated(any(), any());
        }

        @Test
        @DisplayName("SmsService reports a delivery failure → OTP generation still completes (never throws)")
        void smsDeliveryFails_stillCompletes() {
            OtpVerificationRepository repo = mock(OtpVerificationRepository.class);
            UserRepository userRepository = mock(UserRepository.class);
            OtpEventProducer otpEventProducer = mock(OtpEventProducer.class);
            SmsService smsService = mock(SmsService.class);
            when(userRepository.findByMobileAndDeletedFalse(any())).thenReturn(Optional.empty());
            when(smsService.sendSms(any(), any(), any())).thenReturn(SmsSendResult.failure("gateway unreachable"));
            OtpService svc = newService(repo, userRepository, otpEventProducer, mock(AuditLogService.class), smsService, defaultProperties());

            org.assertj.core.api.Assertions.assertThatNoException()
                    .isThrownBy(() -> svc.generateAndSend("9876543210", OtpType.LOGIN));

            verify(repo).save(any(OtpVerification.class));
        }

        @Test
        @DisplayName("a prior OTP for the same mobile+type was created within the cooldown window → throws, no new OTP sent")
        void withinCooldown_throws() {
            OtpVerificationRepository repo = mock(OtpVerificationRepository.class);
            OtpVerification recent = OtpVerification.builder()
                    .mobile("9876543210").otp("hash").otpType(OtpType.LOGIN)
                    .createdAt(LocalDateTime.now().minusSeconds(5)).build();
            when(repo.findTopByMobileAndOtpTypeOrderByCreatedAtDesc("9876543210", OtpType.LOGIN))
                    .thenReturn(Optional.of(recent));
            SmsService smsService = mock(SmsService.class);
            OtpService svc = newService(repo, mock(UserRepository.class), mock(OtpEventProducer.class),
                    mock(AuditLogService.class), smsService, defaultProperties());

            assertThatThrownBy(() -> svc.generateAndSend("9876543210", OtpType.LOGIN))
                    .isInstanceOf(AuthException.class)
                    .hasMessageContaining("wait");
            verify(repo, never()).save(any());
            verifyNoInteractions(smsService);
        }

        @Test
        @DisplayName("resend count within the window already at the configured max → throws")
        void maxResendsReached_throws() {
            OtpVerificationRepository repo = mock(OtpVerificationRepository.class);
            when(repo.findTopByMobileAndOtpTypeOrderByCreatedAtDesc(any(), any())).thenReturn(Optional.empty());
            when(repo.countByMobileAndOtpTypeAndCreatedAtAfter(eq("9876543210"), eq(OtpType.LOGIN), any()))
                    .thenReturn(3L);
            OtpProperties properties = defaultProperties();
            properties.setMaxResends(3);
            OtpService svc = newService(repo, mock(UserRepository.class), mock(OtpEventProducer.class),
                    mock(AuditLogService.class), mock(SmsService.class), properties);

            assertThatThrownBy(() -> svc.generateAndSend("9876543210", OtpType.LOGIN))
                    .isInstanceOf(AuthException.class)
                    .hasMessageContaining("Too many");
            verify(repo, never()).save(any());
        }
    }

    @Nested
    @DisplayName("verify()")
    class Verify {

        private OtpVerification hashedRecord(String rawOtp, int attempts) {
            return OtpVerification.builder()
                    .mobile("9876543210").otp(passwordEncoder.encode(rawOtp)).otpType(OtpType.LOGIN)
                    .used(false).attempts(attempts).expiresAt(LocalDateTime.now().plusMinutes(5)).build();
        }

        @Test
        @DisplayName("correct, unexpired OTP → marks it used")
        void validOtp_marksUsed() {
            OtpVerificationRepository repo = mock(OtpVerificationRepository.class);
            OtpService svc = newService(repo, mock(UserRepository.class), mock(OtpEventProducer.class),
                    mock(AuditLogService.class), mock(SmsService.class), defaultProperties());

            OtpVerification record = hashedRecord("123456", 0);
            when(repo.findTopByMobileAndOtpTypeAndUsedFalseOrderByCreatedAtDesc("9876543210", OtpType.LOGIN))
                    .thenReturn(Optional.of(record));

            svc.verify("9876543210", "123456", OtpType.LOGIN);

            assertThat(record.isUsed()).isTrue();
            verify(repo).save(record);
        }

        @Test
        @DisplayName("no OTP on record → throws AuthException")
        void noRecord_throws() {
            OtpVerificationRepository repo = mock(OtpVerificationRepository.class);
            OtpService svc = newService(repo, mock(UserRepository.class), mock(OtpEventProducer.class),
                    mock(AuditLogService.class), mock(SmsService.class), defaultProperties());
            when(repo.findTopByMobileAndOtpTypeAndUsedFalseOrderByCreatedAtDesc(any(), any()))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> svc.verify("9876543210", "123456", OtpType.LOGIN))
                    .isInstanceOf(AuthException.class)
                    .hasMessageContaining("No OTP found");
        }

        @Test
        @DisplayName("expired OTP → throws AuthException")
        void expired_throws() {
            OtpVerificationRepository repo = mock(OtpVerificationRepository.class);
            OtpService svc = newService(repo, mock(UserRepository.class), mock(OtpEventProducer.class),
                    mock(AuditLogService.class), mock(SmsService.class), defaultProperties());
            OtpVerification record = OtpVerification.builder()
                    .mobile("9876543210").otp(passwordEncoder.encode("123456")).otpType(OtpType.LOGIN)
                    .used(false).expiresAt(LocalDateTime.now().minusMinutes(1)).build();
            when(repo.findTopByMobileAndOtpTypeAndUsedFalseOrderByCreatedAtDesc(any(), any()))
                    .thenReturn(Optional.of(record));

            assertThatThrownBy(() -> svc.verify("9876543210", "123456", OtpType.LOGIN))
                    .isInstanceOf(AuthException.class)
                    .hasMessageContaining("expired");
        }

        @Test
        @DisplayName("wrong OTP value → throws AuthException and increments the attempt counter")
        void wrongOtp_throwsAndIncrementsAttempts() {
            OtpVerificationRepository repo = mock(OtpVerificationRepository.class);
            OtpService svc = newService(repo, mock(UserRepository.class), mock(OtpEventProducer.class),
                    mock(AuditLogService.class), mock(SmsService.class), defaultProperties());
            OtpVerification record = hashedRecord("123456", 0);
            when(repo.findTopByMobileAndOtpTypeAndUsedFalseOrderByCreatedAtDesc(any(), any()))
                    .thenReturn(Optional.of(record));

            assertThatThrownBy(() -> svc.verify("9876543210", "000000", OtpType.LOGIN))
                    .isInstanceOf(AuthException.class)
                    .hasMessageContaining("Invalid OTP");

            assertThat(record.getAttempts()).isEqualTo(1);
            assertThat(record.isUsed()).isFalse(); // still usable for the next attempt
            verify(repo).save(record);
        }

        @Test
        @DisplayName("attempts already at the configured max → throws without comparing, burns the record")
        void maxAttemptsReached_burnsRecord() {
            OtpVerificationRepository repo = mock(OtpVerificationRepository.class);
            OtpProperties properties = defaultProperties();
            properties.setMaxAttempts(5);
            OtpService svc = newService(repo, mock(UserRepository.class), mock(OtpEventProducer.class),
                    mock(AuditLogService.class), mock(SmsService.class), properties);
            // Even the CORRECT code is rejected once attempts are exhausted.
            OtpVerification record = hashedRecord("123456", 5);
            when(repo.findTopByMobileAndOtpTypeAndUsedFalseOrderByCreatedAtDesc(any(), any()))
                    .thenReturn(Optional.of(record));

            assertThatThrownBy(() -> svc.verify("9876543210", "123456", OtpType.LOGIN))
                    .isInstanceOf(AuthException.class)
                    .hasMessageContaining("Too many");

            assertThat(record.isUsed()).isTrue();
            verify(repo).save(record);
        }
    }
}
