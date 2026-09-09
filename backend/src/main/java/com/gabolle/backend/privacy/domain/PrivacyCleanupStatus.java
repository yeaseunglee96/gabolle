package com.gabolle.backend.privacy.domain;

/** {@link PrivacyCleanupRun} 한 번의 실행이 지금 어느 상태인가. */
public enum PrivacyCleanupStatus {

	/** 아직 끝나지 않았다. 정상 흐름이면 이 상태로 남는 행이 없어야 한다. */
	RUNNING,

	/** 삭제까지 전부 끝났다. */
	SUCCEEDED,

	/** 도중에 예외가 났다. {@code errorMessage} 를 본다. */
	FAILED
}
