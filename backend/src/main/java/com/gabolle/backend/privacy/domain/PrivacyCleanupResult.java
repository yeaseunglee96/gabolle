package com.gabolle.backend.privacy.domain;

/** {@link com.gabolle.backend.privacy.application.PrivacyCleanupService#cleanup()} 한 번의 카테고리별 삭제 건수. */
public record PrivacyCleanupResult(int sessionsDeleted, int refreshTokensDeleted, int eventsDeleted) {

	public int total() {
		return sessionsDeleted + refreshTokensDeleted + eventsDeleted;
	}
}
