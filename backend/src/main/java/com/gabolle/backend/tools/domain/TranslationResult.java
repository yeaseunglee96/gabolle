package com.gabolle.backend.tools.domain;

/** {@code provider} 는 캐시 히트면 {@code null} 이다 — 캐시에 처음 담길 때의 업체 이름을 모른다. */
public record TranslationResult(String translatedText, boolean cached, String provider) {
}
