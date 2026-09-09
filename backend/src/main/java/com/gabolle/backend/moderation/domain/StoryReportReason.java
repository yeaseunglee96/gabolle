package com.gabolle.backend.moderation.domain;

import java.util.Arrays;
import java.util.Optional;

/**
 * 신고 사유 — S15P21E201-254.
 *
 * <p>🔴 사유를 자유 입력으로 받지 않는 이유는 검토 화면에서 <b>묶어 세기</b> 위해서다. 같은
 * 기록에 신고가 여러 건 오면 사유를 모아 보여줘야 하는데(`-267`), 자유 입력이면 같은 뜻의
 * 다른 문장이 각각 한 줄이 되어 운영자가 무엇 때문에 신고됐는지 못 읽는다.
 *
 * <p>{@link #OTHER} 에만 자유 입력({@code detail})을 붙인다.
 */
public enum StoryReportReason {

	/** 개인정보 노출. 가장 급한 종류다 — 즉시 비노출이 이것 때문에 필요하다. */
	PRIVACY("개인정보 노출"),

	OFFENSIVE("불쾌한 내용"),

	SPAM("스팸"),

	/** 그 밖. 자유 입력을 함께 받는다. */
	OTHER("기타");

	private final String labelKo;

	StoryReportReason(String labelKo) {
		this.labelKo = labelKo;
	}

	/** 검토 화면에 보여줄 한국어 이름. 서버가 주는 이유는 배포된 앱을 즉시 못 고치기 때문이다. */
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
