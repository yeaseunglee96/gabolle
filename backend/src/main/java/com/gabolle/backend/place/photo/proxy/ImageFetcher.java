package com.gabolle.backend.place.photo.proxy;

import java.net.URI;

/** 상대 서버에서 응답 하나를 받는다. 리디렉트는 따라가지 않는다 — 따라갈지는 {@link ImageProxyService} 가 정한다. */
public interface ImageFetcher {

	Response get(URI uri);

	/**
	 * @param status HTTP 상태
	 * @param location 3xx 일 때 Location 머리(없으면 null)
	 * @param contentType 상대가 붙인 Content-Type(없으면 빈 글자)
	 * @param body 2xx 일 때 본문. 상한을 넘으면 {@code tooLarge} 가 참이고 본문은 비어 있다
	 */
	record Response(int status, String location, String contentType, byte[] body, boolean tooLarge) {
	}

	/** 닿지 못했다(시간 초과·인증서·끊김). */
	class Unreachable extends RuntimeException {

		public Unreachable(String message, Throwable cause) {
			super(message, cause);
		}
	}
}
