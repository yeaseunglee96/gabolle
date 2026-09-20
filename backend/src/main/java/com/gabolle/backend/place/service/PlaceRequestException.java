package com.gabolle.backend.place.service;

import java.util.List;

/**
 * 요청 자체가 계약과 다르다 — 400 으로 나간다. {@code message} 에는 메시지 키가 아니라 사람이
 * 읽는 한국어 문장을 넣는다. 프런트가 이 값을 그대로 화면에 띄운다.
 */
public class PlaceRequestException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	private final String code;

	private final List<String> fields;

	public PlaceRequestException(String code, String message) {
		this(code, message, List.of());
	}

	public PlaceRequestException(String code, String message, List<String> fields) {
		super(message);
		this.code = code;
		this.fields = fields;
	}

	public String getCode() {
		return this.code;
	}

	public List<String> getFields() {
		return this.fields;
	}
}
