package com.gabolle.backend.common.privacy;

import java.lang.reflect.Array;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

/**
 * 일반 추천 로그(Job · Candidate · Outbox 의 JSONB)에 개인정보가 섞이는 것을 저장 직전에 막는다.
 * 민감 제약은 값이 아니라 코드나 스냅샷 ID 로만 연결한다.
 *
 * <p>뻔한 키 이름과 뻔한 형식만 잡으므로 완전하지 않다 — 이름도 형식도 평범한 좌표 한 개는
 * 못 잡는다. 최후의 그물이지 설계 대체물이 아니다.
 */
@Component
public class SensitivePayloadGuard {

	/** 이 이름의 키는 그 자체로 거부한다. */
	private static final Set<String> DENIED_KEYS = Set.of(
			"name", "username", "user_name", "real_name", "full_name", "display_name",
			"email", "email_address", "mail",
			"phone", "phone_number", "mobile", "tel", "telephone",
			"address", "road_address", "jibun_address", "detail_address",
			"lat", "lon", "lng", "latitude", "longitude", "location", "current_location",
			// 조각 목록에 "lat" 을 넣으면 translate · latency · plate 가 걸린다.
			// 그래서 실제로 쓰이는 이름만 완전일치로 더한다.
			"currentlat", "currentlng", "currentlon", "current_lat", "current_lng", "current_lon",
			"originlat", "originlng", "origin_lat", "origin_lng",
			"pickuplat", "pickuplng", "pickup_lat", "pickup_lng",
			"coord", "coords", "coordinate", "coordinates", "point", "geo",
			"track", "trace", "trajectory", "path_points",
			"ad_id", "adid", "idfa", "gaid", "advertising_id", "device_id",
			"ssn", "passport_no", "birth_date", "birthdate");

	/** 이 조각이 키 이름 안 어디에 있어도 거부한다. */
	private static final List<String> DENIED_KEY_FRAGMENTS = List.of(
			"gps", "latitude", "longitude", "allerg", "advertis", "email", "phone",
			"raw_response", "kakao_raw", "naver_raw", "map_raw", "free_text", "freetext");

	private static final Pattern EMAIL = Pattern.compile("[\\w.+-]+@[\\w-]+\\.[\\w.-]+");

	/** 한국 휴대전화 번호. 하이픈이 있어도 없어도 잡는다. */
	private static final Pattern KR_PHONE = Pattern.compile("\\b01[0-9][-. ]?\\d{3,4}[-. ]?\\d{4}\\b");

	/** {@code 35.1796, 129.0756} 처럼 소수점 넷 자리 이상인 좌표 쌍. 대략 10m 이하 정밀도다. */
	private static final Pattern COORDINATE_PAIR = Pattern
			.compile("-?\\d{1,3}\\.\\d{4,}\\s*,\\s*-?\\d{1,3}\\.\\d{4,}");

	/** 중첩이 이보다 깊으면 로그 페이로드로서 이미 잘못됐다. 무한 재귀도 함께 막는다. */
	private static final int MAX_DEPTH = 12;

	/** {@code rootPath} 는 오류 메시지에 쓸 이름이다. 예: {@code feature_values} */
	public void verify(Object root, String rootPath) {
		walk(root, rootPath, 0);
	}

	private void walk(Object node, String path, int depth) {
		if (depth > MAX_DEPTH) {
			throw new SensitiveDataInPayloadException(path, "중첩이 너무 깊다 (최대 " + MAX_DEPTH + ")");
		}
		if (node == null) {
			return;
		}
		if (node instanceof Map<?, ?> map) {
			for (Map.Entry<?, ?> entry : map.entrySet()) {
				String key = String.valueOf(entry.getKey());
				String childPath = path + "." + key;
				verifyKey(key, childPath);
				walk(entry.getValue(), childPath, depth + 1);
			}
			return;
		}
		if (node instanceof Collection<?> collection) {
			int index = 0;
			for (Object element : collection) {
				walk(element, path + "[" + index + "]", depth + 1);
				index++;
			}
			return;
		}
		if (node.getClass().isArray()) {
			// double[] 같은 원시 타입 배열은 Object[] 로 캐스팅되지 않아 Array.get 을 쓴다.
			int length = Array.getLength(node);
			for (int i = 0; i < length; i++) {
				walk(Array.get(node, i), path + "[" + i + "]", depth + 1);
			}
			return;
		}
		if (node instanceof CharSequence text) {
			verifyText(text.toString(), path);
		}
	}

	private void verifyKey(String rawKey, String path) {
		String key = rawKey.toLowerCase().replace('-', '_');
		if (DENIED_KEYS.contains(key)) {
			throw new SensitiveDataInPayloadException(path, "일반 로그에 금지된 키 이름이다: " + rawKey);
		}
		for (String fragment : DENIED_KEY_FRAGMENTS) {
			if (key.contains(fragment)) {
				throw new SensitiveDataInPayloadException(path,
						"일반 로그에 금지된 키 조각을 포함한다: " + fragment);
			}
		}
	}

	private void verifyText(String value, String path) {
		if (EMAIL.matcher(value).find()) {
			throw new SensitiveDataInPayloadException(path, "이메일 주소로 보이는 값이다");
		}
		if (KR_PHONE.matcher(value).find()) {
			throw new SensitiveDataInPayloadException(path, "전화번호로 보이는 값이다");
		}
		if (COORDINATE_PAIR.matcher(value).find()) {
			throw new SensitiveDataInPayloadException(path, "정밀 좌표로 보이는 값이다");
		}
	}
}
