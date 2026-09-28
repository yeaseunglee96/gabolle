package com.gabolle.backend.functional.support;

import java.net.URI;

import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;

/**
 * 로그인 토큰을 매 요청의 헤더에 실어 보내는 얇은 래퍼. {@link TestRestTemplate} 은 컨텍스트당 하나뿐인
 * 공유 빈이라, 토큰을 그 빈의 인터셉터에 꽂으면 다음 테스트 메서드가 앞 테스트의 토큰을 물려받는다.
 * 그래서 토큰은 이 인스턴스의 매 요청 헤더에만 싣는다.
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
	 * 본문을 실은 {@code DELETE}. 탈퇴 계약이 그렇다 — 확인 값이나 비밀번호를 질의 문자열에 넣으면
	 * 접속 기록에 남는다. {@code TestRestTemplate.delete} 는 본문을 못 실어 {@code exchange} 를 쓴다.
	 */
	public <T> ResponseEntity<T> delete(String path, Object body, Class<T> responseType) {
		return rest.exchange(path, HttpMethod.DELETE, authed(body), responseType);
	}

	/**
	 * 파일 업로드. {@code multipart/form-data} 는 헤더의 Content-Type 이 경계(boundary)를 정하므로
	 * {@link #authed} 가 만드는 JSON 헤더를 그대로 못 쓴다.
	 */
	public <T> ResponseEntity<T> postMultipart(String path, MultiValueMap<String, Object> parts,
			ParameterizedTypeReference<T> responseType) {
		HttpHeaders headers = new HttpHeaders();
		headers.setBearerAuth(bearerToken);
		headers.setContentType(MediaType.MULTIPART_FORM_DATA);
		return rest.exchange(path, HttpMethod.POST, new HttpEntity<>(parts, headers), responseType);
	}

	/**
	 * 주소를 다시 인코딩하지 않고 그대로 보내는 {@code GET}. 경로 문자열을 주면 {@code TestRestTemplate} 이 {@code %} 를
	 * {@code %25} 로 바꿔 버려, 잘못 인코딩된 요청(예: CP949 검색어)을 그대로 재현할 수 없다.
	 */
	public <T> ResponseEntity<T> getVerbatim(String pathAndQuery, HttpHeaders extraHeaders,
			ParameterizedTypeReference<T> responseType) {
		HttpHeaders headers = new HttpHeaders();
		headers.addAll(extraHeaders);
		headers.setBearerAuth(bearerToken);
		return rest.exchange(URI.create(rest.getRootUri() + pathAndQuery), HttpMethod.GET, new HttpEntity<>(headers),
				responseType);
	}

	private <B> HttpEntity<B> authed(B body) {
		HttpHeaders headers = new HttpHeaders();
		headers.setBearerAuth(bearerToken); // 이 인스턴스에만 붙는다 — 공유 빈은 안 건드린다
		return new HttpEntity<>(body, headers);
	}
}
