package com.gabolle.backend.appversion.domain;

/**
 * 한 플랫폼(ios·android)의 배포 정보. 앱이 켜질 때 자기 버전과 견준다.
 *
 * @param latestVersion       스토어에 올라간 최신 판. 앱이 이보다 낮으면 「새 버전이 나왔어요」를 띄운다
 * @param minSupportedVersion 이보다 낮은 앱은 업데이트 말고는 아무것도 못 하게 막는다(강제 업데이트)
 * @param storeUrl            「업데이트」 단추가 여는 스토어 주소
 */
public record AppRelease(String platform, String latestVersion, String minSupportedVersion, String storeUrl) {
}
