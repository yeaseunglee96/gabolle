package com.gabolle.backend.moderation.domain;

import java.util.Arrays;
import java.util.Optional;

/**
 * 신고 사유. 검토 화면이 한 기록의 여러 신고를 사유별로 묶어 세야 해서 자유 입력으로 받지 않는다.
 * {@link #OTHER} 에만 자유 입력({@code detail})을 붙인다.
 */
public enum StoryReportReason {

	/** 즉시 비노출이 필요한 이유가 된 사유다. */
	PRIVACY("개인정보 노출"),

	OFFENSIVE("불쾌한 내용"),

	SPAM("스팸"),

	/** 그 밖. 자유 입력을 함께 받는다. */
	OTHER("기타");

	private final String labelKo;

	StoryReportReason(String labelKo) {
		this.labelKo = labelKo;
	}

	/** 검토 화면에 보여줄 한국어 이름. 배포된 앱을 즉시 못 고치므로 서버가 준다. */
	public String labelKo() {
		return this.labelKo;
	}

	/** 모르는 값은 빈 값이다 — 400 으로 거절할지는 부르는 쪽이 정한다. */
	public static Optional<StoryReportReason> from(String value) {
		if (value == null || value.isBlank()) {
			return Optional.empty();
		}
		String normalized = value.trim().toUpperCase();
		return Arrays.stream(values()).filter(it -> it.name().equals(normalized)).findFirst();
	}
}
