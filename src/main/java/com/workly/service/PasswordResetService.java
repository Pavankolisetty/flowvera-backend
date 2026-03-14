package com.workly.service;

import com.workly.dto.ForgotPasswordRequest;
import com.workly.dto.MessageResponse;
import com.workly.dto.ResetPasswordWithOtpRequest;
import com.workly.dto.VerifyPasswordResetOtpRequest;
import com.workly.dto.VerifyPasswordResetOtpResponse;

public interface PasswordResetService {
    MessageResponse sendOtp(ForgotPasswordRequest request);
    VerifyPasswordResetOtpResponse verifyOtp(VerifyPasswordResetOtpRequest request);
    MessageResponse resetPassword(ResetPasswordWithOtpRequest request);
}
