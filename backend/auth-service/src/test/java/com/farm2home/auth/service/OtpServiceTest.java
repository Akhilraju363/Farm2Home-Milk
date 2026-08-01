package com.farm2home.auth.service;

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

    @Nested
    @DisplayName("generateAndSend()")
    class GenerateAndSend {

        @Test
        @DisplayName("invalidates previous OTPs and saves a new 6-digit one")
        void savesNewOtp() {
            OtpVerificationRepository repo = mock(OtpVerificationRepository.class);
            UserRepository userRepository = mock(UserRepository.class);
            OtpEventProducer otpEventProducer = mock(OtpEventProducer.class);
            AuditLogService auditLogService = mock(AuditLogService.class);
            SmsService smsService = mock(SmsService.class);
            when(userRepository.findByMobileAndDeletedFalse(any())).thenReturn(Optional.empty());
            OtpService svc = new OtpService(repo, userRepository, otpEventProducer, auditLogService, smsService);

            svc.generateAndSend("9876543210", OtpType.LOGIN);

            verify(repo).markAllUsedByMobileAndType("9876543210", OtpType.LOGIN);
            verify(auditLogService).record(argThat(entry ->
                    entry.getAction().equals(com.farm2home.common.core.audit.AuditAction.OTP_SENT)
                            && "9876543210".equals(entry.getUsername())));
            verify(smsService).sendSms(eq("9876543210"), contains("OTP"), eq(EmailTemplateConstants.EVENT_OTP));

            ArgumentCaptor<OtpVerification> captor = ArgumentCaptor.forClass(OtpVerification.class);
            verify(repo).save(captor.capture());
            OtpVerification saved = captor.getValue();
            assertThat(saved.getMobile()).isEqualTo("9876543210");
            assertThat(saved.getOtpType()).isEqualTo(OtpType.LOGIN);
            assertThat(saved.getOtp()).matches("\\d{6}");
            assertThat(saved.isUsed()).isFalse();
            assertThat(saved.getExpiresAt()).isAfter(LocalDateTime.now());
            verify(otpEventProducer, never()).publishOtpGenerated(any(), any());
        }

        @Test
        @DisplayName("matching user with an email on file → also publishes an OTP email event")
        void userWithEmail_publishesEvent() {
            OtpVerificationRepository repo = mock(OtpVerificationRepository.class);
            UserRepository userRepository = mock(UserRepository.class);
            OtpEventProducer otpEventProducer = mock(OtpEventProducer.class);
            User user = User.builder().id(UUID.randomUUID()).mobile("9876543210")
                    .email("test@example.com").username("test.user").build();
            when(userRepository.findByMobileAndDeletedFalse("9876543210")).thenReturn(Optional.of(user));
            OtpService svc = new OtpService(repo, userRepository, otpEventProducer, mock(AuditLogService.class), mock(SmsService.class));

            svc.generateAndSend("9876543210", OtpType.LOGIN);

            verify(otpEventProducer).publishOtpGenerated(eq(user), any());
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
            OtpService svc = new OtpService(repo, userRepository, otpEventProducer, mock(AuditLogService.class), mock(SmsService.class));

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
            OtpService svc = new OtpService(repo, userRepository, otpEventProducer, mock(AuditLogService.class), smsService);

            org.assertj.core.api.Assertions.assertThatNoException()
                    .isThrownBy(() -> svc.generateAndSend("9876543210", OtpType.LOGIN));

            verify(repo).save(any(OtpVerification.class));
        }
    }

    @Nested
    @DisplayName("verify()")
    class Verify {

        @Test
        @DisplayName("correct, unexpired OTP → marks it used")
        void validOtp_marksUsed() {
            OtpVerificationRepository repo = mock(OtpVerificationRepository.class);
            OtpService svc = new OtpService(repo, mock(UserRepository.class), mock(OtpEventProducer.class), mock(AuditLogService.class), mock(SmsService.class));

            OtpVerification record = OtpVerification.builder()
                    .mobile("9876543210").otp("123456").otpType(OtpType.LOGIN)
                    .used(false).expiresAt(LocalDateTime.now().plusMinutes(5)).build();
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
            OtpService svc = new OtpService(repo, mock(UserRepository.class), mock(OtpEventProducer.class), mock(AuditLogService.class), mock(SmsService.class));
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
            OtpService svc = new OtpService(repo, mock(UserRepository.class), mock(OtpEventProducer.class), mock(AuditLogService.class), mock(SmsService.class));
            OtpVerification record = OtpVerification.builder()
                    .mobile("9876543210").otp("123456").otpType(OtpType.LOGIN)
                    .used(false).expiresAt(LocalDateTime.now().minusMinutes(1)).build();
            when(repo.findTopByMobileAndOtpTypeAndUsedFalseOrderByCreatedAtDesc(any(), any()))
                    .thenReturn(Optional.of(record));

            assertThatThrownBy(() -> svc.verify("9876543210", "123456", OtpType.LOGIN))
                    .isInstanceOf(AuthException.class)
                    .hasMessageContaining("expired");
        }

        @Test
        @DisplayName("wrong OTP value → throws AuthException")
        void wrongOtp_throws() {
            OtpVerificationRepository repo = mock(OtpVerificationRepository.class);
            OtpService svc = new OtpService(repo, mock(UserRepository.class), mock(OtpEventProducer.class), mock(AuditLogService.class), mock(SmsService.class));
            OtpVerification record = OtpVerification.builder()
                    .mobile("9876543210").otp("123456").otpType(OtpType.LOGIN)
                    .used(false).expiresAt(LocalDateTime.now().plusMinutes(5)).build();
            when(repo.findTopByMobileAndOtpTypeAndUsedFalseOrderByCreatedAtDesc(any(), any()))
                    .thenReturn(Optional.of(record));

            assertThatThrownBy(() -> svc.verify("9876543210", "000000", OtpType.LOGIN))
                    .isInstanceOf(AuthException.class)
                    .hasMessageContaining("Invalid OTP");
        }
    }
}
