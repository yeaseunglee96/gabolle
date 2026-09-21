package com.gabolle.backend.common.json;

import org.springframework.stereotype.Component;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * JSONB 컬럼에 넣을 문자열을 만든다. 직렬화를 엔티티가 아니라 여기서 하는 이유는 저장 직전에
 * 개인정보 검사를 한 번 통과시키기 위해서다.
 *
 * <p>Jackson 은 {@code tools.jackson} 이다. 옛 {@code com.fasterxml.jackson} 도 클래스패스에 있어서
 * import 를 틀리면 컴파일은 되고 빈 주입에서만 터진다.
 */
@Component
public class JsonPayloads {

	private final ObjectMapper objectMapper;

	public JsonPayloads(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	public String write(Object value, String fallbackWhenNull) {
		if (value == null) {
			return fallbackWhenNull;
		}
		try {
			return this.objectMapper.writeValueAsString(value);
		}
		catch (JacksonException ex) {
			throw new IllegalArgumentException("JSONB 컬럼으로 직렬화할 수 없다: " + value.getClass().getName(), ex);
		}
	}

	/** 객체 하나. 값이 없으면 빈 객체 {@code {}} 다 — SQL NULL 과 구분한다. */
	public String writeObject(Object value) {
		return write(value, "{}");
	}

	/** 배열 하나. 값이 없으면 빈 배열 {@code []} 다. */
	public String writeArray(Object value) {
		return write(value, "[]");
	}
}
