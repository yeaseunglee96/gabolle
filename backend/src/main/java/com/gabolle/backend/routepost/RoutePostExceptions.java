package com.gabolle.backend.routepost;

/** 루트 포스트가 던지는 예외들. 응답 코드는 {@code RoutePostExceptionHandler} 가 정한다. */
public final class RoutePostExceptions {

	private RoutePostExceptions() {
	}

	/** 없음·지움·차단 — 모두 같은 404. 응답 차이로 글이 있다는 사실이 새지 않게. */
	public static class NotFoundException extends RuntimeException {

		public NotFoundException() {
			super("ROUTE_POST_NOT_FOUND");
		}
	}

	/** 내 글이 아닌 것을 지우려 했다. 볼 수 있는 글이므로 존재는 이미 안다 — 403. */
	public static class ForbiddenException extends RuntimeException {

		public ForbiddenException() {
			super("ROUTE_POST_FORBIDDEN");
		}
	}

	/** 올릴 수 없는 경로 — 그날이 없거나 숙소를 빼면 두 곳이 안 된다. 코드가 메시지다. */
	public static class InvalidRouteException extends RuntimeException {

		public InvalidRouteException(String code) {
			super(code);
		}
	}

	/** 일정이 없거나 내 일정이 아니다. 남의 일정을 올릴 수 없다. */
	public static class SourceNotFoundException extends RuntimeException {

		public SourceNotFoundException() {
			super("ITINERARY_NOT_FOUND");
		}
	}
}
