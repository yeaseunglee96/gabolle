package com.gabolle.backend.common.privacy;

/** 일반 로그·이벤트 JSONB 에 넣으면 안 되는 값이 발견됐다. 삼키면 개인정보가 그냥 들어가므로 삼키지 않는다. */
public class SensitiveDataInPayloadException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	private final String path;

	public SensitiveDataInPayloadException(String path, String reason) {
		super("일반 로그에 넣을 수 없는 값이다: " + path + " — " + reason);
		this.path = path;
	}

	/** 문제가 된 위치. 예: {@code feature_values.origin.lat} */
	public String getPath() {
		return path;
	}
}
