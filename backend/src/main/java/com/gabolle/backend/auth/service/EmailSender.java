package com.gabolle.backend.auth.service;

public interface EmailSender {

	void sendEmailVerification(String email, String verificationUrl);

	void sendPasswordReset(String email, String resetUrl);
}
