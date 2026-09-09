package com.gabolle.backend.common.json;

import org.springframework.stereotype.Component;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * JSONB 컬럼에 넣을 문자열을 만든다.
 *
 * <p>엔티티는 JSON 을 문자열로 들고 있고 Hibernate 가 그것을 PostgreSQL 의 jsonb 로 넘긴다.
 * 직렬화를 엔티티가 아니라 여기서 하는 이유는 <b>저장 직전에 개인정보 검사를 한 번 통과</b>
 * 시키기 위해서다 — 엔티티 안에서 직렬화하면 검사를 우회하는 경로가 생긴다.
 *
 * <p>🔴 {@code tools.jackson} 이지 {@code com.fasterxml.jackson} 이 아니다. Spring Boot 4 는
 * Jackson 3 을 쓰고, 거기서 패키지 이름이 통째로 바뀌었다. 옛 패키지도 다른 라이브러리를 타고
 * 클래스패스에 들어와 있어서 <b>import 만 틀리면 컴파일은 되고 빈 주입에서만 터진다.</b>
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
