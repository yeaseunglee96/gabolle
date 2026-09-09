package com.gabolle.backend.common.api;

public record ApiResponse<T>(T data, ApiError error, ApiMeta meta) {

	public static <T> ApiResponse<T> success(T data, String requestId) {
		return new ApiResponse<>(data, null, new ApiMeta(requestId));
	}

	public static <T> ApiResponse<T> failure(ApiError error, String requestId) {
		return new ApiResponse<>(null, error, new ApiMeta(requestId));
	}
}
