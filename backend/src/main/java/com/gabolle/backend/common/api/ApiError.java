package com.gabolle.backend.common.api;

import java.util.List;

public record ApiError(String code, String message, List<String> fields) {

	public ApiError(String code, String message) {
		this(code, message, List.of());
	}
}
