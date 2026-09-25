package com.gabolle.backend.place;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.gabolle.backend.place.service.ChainBrand;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 상표 사전 (S15P21E201-1616). 이름은 전부 운영에 실린 모양 그대로다(2026-09-25) — 지어낸 이름으로 검사하면 운영이
 * 실제로 어떻게 섞여 오는지를 놓친다.
 */
class ChainBrandTest {

	@Test
	@DisplayName("🔴 띄어쓰기에 기대지 않는다 — 붙여 쓴 이름도, 상표 안을 띄운 이름도 잡는다")
	void spacingDoesNotMatter() {
		assertThat(ChainBrand.brandOf("배스킨라빈스광안역점")).isEqualTo("배스킨라빈스");
		assertThat(ChainBrand.brandOf("파리바게뜨민락동방점")).isEqualTo("파리바게뜨");
		assertThat(ChainBrand.brandOf("투썸 플레이스 괴정점")).isEqualTo("투썸플레이스");
		assertThat(ChainBrand.brandOf("유가네 닭갈비 평산점")).isEqualTo("유가네닭갈비");
		// 상표 「안」에 빈칸이 있는 운영 이름 — 빈칸을 빼지 않으면 별칭으로 시작하지 않는다.
		assertThat(ChainBrand.brandOf("Tom N Toms Coffee")).isEqualTo("탐앤탐스");
		assertThat(ChainBrand.brandOf("Compose Coffee")).isEqualTo("컴포즈커피");
		assertThat(ChainBrand.brandOf("Pizza Hut")).isEqualTo("피자헛");
		assertThat(ChainBrand.brandOf("하삼동 커피")).isEqualTo("하삼동커피");
	}

	@Test
	@DisplayName("영어 이름과 흔들리는 표기도 같은 상표다")
	void englishNamesAndVariantSpellings() {
		assertThat(ChainBrand.brandOf("Starbucks")).isEqualTo("스타벅스");
		assertThat(ChainBrand.brandOf("McDonald")).isEqualTo("맥도날드");
		assertThat(ChainBrand.brandOf("Domino's Pizza")).isEqualTo("도미노피자");
		assertThat(ChainBrand.brandOf("TomnToms Coffee")).isEqualTo("탐앤탐스");
		assertThat(ChainBrand.brandOf("파리바게트 구산신도시점")).isEqualTo("파리바게뜨");
		assertThat(ChainBrand.brandOf("베스킨라빈스")).isEqualTo("배스킨라빈스");
	}

	@Test
	@DisplayName("🔴 부산의 지점 있는 동네 맛집은 체인이 아니다 — 「동네 가게가 먼저」의 동네 가게 쪽이다")
	void localMultiBranchRestaurantsAreNotChains() {
		assertThat(ChainBrand.brandOf("쌍둥이돼지국밥 본점")).isNull();
		assertThat(ChainBrand.brandOf("개미집 서면점")).isNull();
		assertThat(ChainBrand.brandOf("해운대해수욕장")).isNull();
	}

	@Test
	@DisplayName("이름이 없으면 모른다")
	void noNameIsNoBrand() {
		assertThat(ChainBrand.brandOf(null)).isNull();
		assertThat(ChainBrand.brandOf("  ")).isNull();
	}
}
