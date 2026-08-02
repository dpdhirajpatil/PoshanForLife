package com.poshanforlife.api.service;

import com.poshanforlife.api.repository.PhoneOtpRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Persists a failed OTP guess in its own transaction.
 *
 * <p>This exists because of a subtlety that is easy to get wrong: a wrong code
 * makes {@code PhoneOtpService.verify} throw, and throwing rolls its
 * transaction back — taking the attempts increment with it. The counter would
 * reset on every failure and the brute-force limit would never trigger, while
 * still looking correct in a mock-based unit test.
 *
 * <p>REQUIRES_NEW commits the increment independently of that rollback. It's a
 * separate bean rather than a method on PhoneOtpService because Spring's
 * proxying ignores propagation on self-invoked calls.
 */
@Service
@RequiredArgsConstructor
public class OtpAttemptRecorder {

    private final PhoneOtpRepository phoneOtpRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailedAttempt(UUID otpId) {
        phoneOtpRepository.findById(otpId).ifPresent(otp -> {
            otp.setAttempts(otp.getAttempts() + 1);
            phoneOtpRepository.save(otp);
        });
    }

    /**
     * Marks the code used, in its own transaction, so a code can't be replayed
     * even if the action it authorised then fails and rolls back.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markVerified(UUID otpId) {
        phoneOtpRepository.findById(otpId).ifPresent(otp -> {
            otp.setVerified(true);
            phoneOtpRepository.save(otp);
        });
    }

    // Deliberately no `recordFailedAttempt(PhoneOtp)` convenience overload:
    // it would call the annotated method on `this`, bypassing the proxy that
    // applies REQUIRES_NEW, and silently reintroduce the rollback bug this
    // class exists to fix. Callers pass the id.
}
