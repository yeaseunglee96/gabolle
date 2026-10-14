package com.gabolle.backend.facility.api;

import java.util.List;

/**
 * 요청 값이 계약과 다르다 — 400 으로 나간다 (S15P21E201-2052). {@code message} 는 사람이 읽는 한국어 문장이고, 앱이 그대로 띄우지는
 * 않는다(화면이 자기 말로 안내한다). 어느 칸이 문제인지 {@code fields} 로 알린다.
 */
public class FacilityRequestException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	private final List<String> fields;

	public FacilityRequestException(String message, List<String> fields) {
		super(message);
		this.fields = List.copyOf(fields);
	}

	public List<String> getFields() {
		return this.fields;
	}
}
