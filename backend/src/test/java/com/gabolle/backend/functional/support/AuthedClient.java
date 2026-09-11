package com.gabolle.backend.functional.support;

import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;

/**
 * 로그인 토큰을 매 요청의 헤더에 실어 보내는 얇은 래퍼 — S15P21E201-779.
 *
 * <p>🔴 {@link TestRestTemplate}은 테스트 컨텍스트당 하나뿐인 공유 빈이다. 로그인 토큰을 그
 * 빈의 인터셉터 목록에 직접 꽂으면, 같은 컨텍스트를 재사용하는 다음 테스트 메서드가 앞 테스트의
 * 토큰을 그대로 물려받는다 — "이 요청이 정말 이 사용자로 인증됐나"를 더 이상 그 테스트만 보고는
 * 알 수 없게 된다. 그래서 토큰은 공유 빈이 아니라 이 인스턴스의 매 요청 헤더에만 싣는다.
 */
public final class AuthedClient {

	private final TestRestTemplate rest;
	private final String bearerToken;

	AuthedClient(TestRestTemplate rest, String bearerToken) {
		this.rest = rest;
		this.bearerToken = bearerToken;
	}

	public <T> ResponseEntity<T> post(String path, Object body, Class<T> responseType) {
		return rest.exchange(path, HttpMethod.POST, authed(body), responseType);
	}

	public <T> ResponseEntity<T> post(String path, Object body, ParameterizedTypeReference<T> responseType) {
		return rest.exchange(path, HttpMethod.POST, authed(body), responseType);
	}

	public <T> ResponseEntity<T> get(String path, Class<T> responseType) {
		return rest.exchange(path, HttpMethod.GET, authed(null), responseType);
	}

	public <T> ResponseEntity<T> get(String path, ParameterizedTypeReference<T> responseType) {
		return rest.exchange(path, HttpMethod.GET, authed(null), responseType);
	}

	/**
	 * 본문을 실은 {@code DELETE} — S15P21E201-837 (탈퇴).
	 *
	 * <p>🔴 {@code DELETE} 에 본문을 싣는 것은 흔하지 않지만 탈퇴 계약이 그렇다(확인 값이나
	 * 비밀번호를 질의 문자열에 넣으면 접속 기록에 남는다). {@code TestRestTemplate.delete} 는
	 * 본문을 못 실으므로 {@code exchange} 를 쓴다.
	 */
	public <T> ResponseEntity<T> delete(String path, Object body, Class<T> responseType) {
		return rest.exchange(path, HttpMethod.DELETE, authed(body), responseType);
	}

	/**
	 * 파일 업로드 — S15P21E201-782. {@code multipart/form-data}는 본문이 아니라 헤더의
	 * Content-Type이 경계(boundary)를 정하므로, {@link #authed}가 만드는 일반 JSON 헤더를
	 * 그대로 못 쓴다.
	 */
	public <T> ResponseEntity<T> postMultipart(String path, MultiValueMap<String, Object> parts,
			ParameterizedTypeReference<T> responseType) {
		HttpHeaders headers = new HttpHeaders();
		headers.setBearerAuth(bearerToken);
		headers.setContentType(MediaType.MULTIPART_FORM_DATA);
		return rest.exchange(path, HttpMethod.POST, new HttpEntity<>(parts, headers), responseType);
	}

	private <B> HttpEntity<B> authed(B body) {
		HttpHeaders headers = new HttpHeaders();
		headers.setBearerAuth(bearerToken); // 이 인스턴스에만 붙는다 — 공유 빈은 안 건드린다
		return new HttpEntity<>(body, headers);
	}
}
