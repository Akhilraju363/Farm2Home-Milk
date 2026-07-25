package com.farm2home.auth.service;

import com.farm2home.auth.domain.entity.OtpVerification;
import com.farm2home.auth.domain.enums.OtpType;
import com.farm2home.auth.domain.repository.OtpVerificationRepository;
import com.farm2home.auth.exception.AuthException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class OtpServiceTest {

    @Nested
    @DisplayName("generateAndSend()")
    class GenerateAndSend {

        @Test
        @DisplayName("invalidates previous OTPs and saves a new 6-digit one")
        void savesNewOtp() {
            OtpVerificationRepository repo = mock(OtpVerificationRepository.class);
            OtpService svc = new OtpService(repo);

            svc.generateAndSend("9876543210", OtpType.LOGIN);

            verify(repo).markAllUsedByMobileAndType("9876543210", OtpType.LOGIN);

            ArgumentCaptor<OtpVerification> captor = ArgumentCaptor.forClass(OtpVerification.class);
            verify(repo).save(captor.capture());
            OtpVerification saved = captor.getValue();
            assertThat(saved.getMobile()).isEqualTo("9876543210");
            assertThat(saved.getOtpType()).isEqualTo(OtpType.LOGIN);
            assertThat(saved.getOtp()).matches("\\d{6}");
            assertThat(saved.isUsed()).isFalse();
            assertThat(saved.getExpiresAt()).isAfter(LocalDateTime.now());
        }
    }

    @Nested
    @DisplayName("verify()")
    class Verify {

        @Test
        @DisplayName("correct, unexpired OTP → marks it used")
        void validOtp_marksUsed() {
            OtpVerificationRepository repo = mock(OtpVerificationRepository.class);
            OtpService svc = new OtpService(repo);

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
            OtpService svc = new OtpService(repo);
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
            OtpService svc = new OtpService(repo);
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
            OtpService svc = new OtpService(repo);
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
