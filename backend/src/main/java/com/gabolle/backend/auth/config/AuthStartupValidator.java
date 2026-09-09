package com.gabolle.backend.auth.config;

import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile({"db", "dev"})
public class AuthStartupValidator {

	private static final String INSECURE_DEFAULT = "local-development-secret-change-me";
	private final AuthProperties properties;

	public AuthStartupValidator(AuthProperties properties) {
		this.properties = properties;
	}

	@PostConstruct
	void validate() {
		String secret = properties.getJwtSecret();
		if (secret == null || secret.length() < 32 || INSECURE_DEFAULT.equals(secret)) {
			throw new IllegalStateException("GABOLLE_JWT_SECRET must be a non-default value of at least 32 characters");
		}
	}
}
