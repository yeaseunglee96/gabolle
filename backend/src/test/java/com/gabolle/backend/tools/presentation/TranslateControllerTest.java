package com.gabolle.backend.tools.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.gabolle.backend.tools.application.TranslationService;
import com.gabolle.backend.tools.application.TranslationVendorException;
import com.gabolle.backend.tools.application.TranslationVendorPort;
import com.gabolle.backend.tools.config.TranslateProperties;
import com.gabolle.backend.tools.domain.TranslationCacheRepository;
import com.gabolle.backend.tools.domain.TranslationDirection;

/**
 * 컨트롤러와 예외 처리기만 세워 HTTP 계약을 잰다. 캐시 규칙 자체는 {@code TranslationServiceTest} 가
 * 잰다.
 */
class TranslateControllerTest {

	private MockMvc mockMvc;
	private StubVendor vendor;

	@BeforeEach
	void setUp() {
		this.vendor = new StubVendor();
		TranslationCacheRepository cacheRepository = new InMemoryCacheRepository();
		TranslateProperties properties = new TranslateProperties();
		Clock clock = Clock.fixed(Instant.parse("2026-09-09T00:00:00Z"), ZoneOffset.UTC);
		TranslationService service = new TranslationService(this.vendor, cacheRepository, properties, clock);

		this.mockMvc = MockMvcBuilders.standaloneSetup(new TranslateController(service))
				.setControllerAdvice(new TranslateExceptionHandler())
				.build();
	}

	private static Authentication asUser() {
		return new TestingAuthenticationToken(UUID.randomUUID().toString(), null);
	}

	@Test
	@DisplayName("정상 번역은 200 과 번역 문장·캐시 여부·업체 이름을 담아 온다")
	void translationSucceeds() throws Exception {
		this.mockMvc.perform(post("/api/v1/tools/translate")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"sourceText\":\"안녕\",\"direction\":\"KO_TO_EN\"}")
						.principal(asUser()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.translatedText").value("hello"))
				.andExpect(jsonPath("$.data.cached").value(false))
				.andExpect(jsonPath("$.data.provider").value("STUB_VENDOR"));
	}

	@Test
	@DisplayName("🔴 업체 호출이 실패하면 502 이고 응답에 실패가 분명히 담긴다 — 200 으로 숨기지 않는다")
	void vendorFailureIsNotHiddenAs200() throws Exception {
		this.vendor.shouldFail = true;

		this.mockMvc.perform(post("/api/v1/tools/translate")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"sourceText\":\"안녕\",\"direction\":\"KO_TO_EN\"}")
						.principal(asUser()))
				.andExpect(status().isBadGateway())
				.andExpect(jsonPath("$.error.code").value("TRANSLATE_VENDOR_UNAVAILABLE"))
				.andExpect(jsonPath("$.data").doesNotExist());
	}

	@Test
	@DisplayName("빈 문장은 400 이다")
	void blankSourceTextIsRejected() throws Exception {
		this.mockMvc.perform(post("/api/v1/tools/translate")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"sourceText\":\"\",\"direction\":\"KO_TO_EN\"}")
						.principal(asUser()))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("TRANSLATE_INVALID_REQUEST"));
	}

	@Test
	@DisplayName("모르는 방향은 400 이다")
	void unknownDirectionIsRejected() throws Exception {
		this.mockMvc.perform(post("/api/v1/tools/translate")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"sourceText\":\"안녕\",\"direction\":\"KO_TO_MARS\"}")
						.principal(asUser()))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("TRANSLATE_INVALID_REQUEST"));
	}

	/**
	 * 🔴 앱은 이미 이 셋을 보낸다(frontend/src/field/translate.ts, S15P21E201-1361). 서버가 모르면 앱이 영어를
	 * 거쳐 한 번 더 부르고 「영어를 거쳐 번역했어요」라고 알린다 — S15P21E201-1363 이 그 우회를 없앤다.
	 */
	@ParameterizedTest
	@ValueSource(strings = { "JA_TO_KO", "ZH_HANS_TO_KO", "ZH_HANT_TO_KO" })
	@DisplayName("🔴 앱이 보내는 일본어·중국어 방향을 받고, 그 방향 그대로 업체에 넘긴다")
	void acceptsTheDirectionsTheAppSends(String direction) throws Exception {
		this.mockMvc.perform(post("/api/v1/tools/translate")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"sourceText\":\"氷なしでお願いします\",\"direction\":\"" + direction + "\"}")
						.principal(asUser()))
				.andExpect(status().isOk());

		// 받기만 하고 영어 방향으로 바꿔 부르면 겉으로는 똑같이 200 이다. 무엇이 넘어갔는지를 본다.
		assertThat(this.vendor.lastDirection).isEqualTo(TranslationDirection.valueOf(direction));
	}

	@Test
	@DisplayName("모르는 방향을 거절할 때 받을 수 있는 값을 전부 알려 준다 — 손으로 적은 목록이 낡지 않는다")
	void rejectionListsEveryAcceptedDirection() throws Exception {
		this.mockMvc.perform(post("/api/v1/tools/translate")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"sourceText\":\"안녕\",\"direction\":\"KO_TO_MARS\"}")
						.principal(asUser()))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.message").value(org.hamcrest.Matchers.containsString("JA_TO_KO")))
				.andExpect(jsonPath("$.error.message").value(org.hamcrest.Matchers.containsString("KO_TO_ZH_HANT")));
	}

	@Test
	@DisplayName("일괄 번역은 받은 순서대로 items 를 돌려준다")
	void batchReturnsItemsInOrder() throws Exception {
		this.mockMvc.perform(post("/api/v1/tools/translate/batch")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"direction\":\"KO_TO_JA\",\"texts\":[\"해운대\",\"광안리\"]}")
						.principal(asUser()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items.length()").value(2))
				.andExpect(jsonPath("$.data.items[0].status").value("TRANSLATED"))
				.andExpect(jsonPath("$.data.items[1].translatedText").value("hello"));

		assertThat(this.vendor.lastDirection).isEqualTo(TranslationDirection.KO_TO_JA);
	}

	@Test
	@DisplayName("🔴 일괄 번역도 업체가 전부 실패하면 502 다 — 200 에 빈 번역을 담지 않는다")
	void batchVendorFailureIs502() throws Exception {
		this.vendor.shouldFail = true;

		this.mockMvc.perform(post("/api/v1/tools/translate/batch")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"direction\":\"KO_TO_JA\",\"texts\":[\"해운대\"]}")
						.principal(asUser()))
				.andExpect(status().isBadGateway())
				.andExpect(jsonPath("$.error.code").value("TRANSLATE_VENDOR_UNAVAILABLE"));
	}

	@Test
	@DisplayName("일괄 번역에 빈 목록을 주면 400 이다")
	void batchWithNoTextsIsRejected() throws Exception {
		this.mockMvc.perform(post("/api/v1/tools/translate/batch")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"direction\":\"KO_TO_JA\",\"texts\":[]}")
						.principal(asUser()))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("TRANSLATE_INVALID_REQUEST"));
	}

	/** 여러 테스트가 함께 참조할 수 있게 필드로 둔다 — 요청마다 실패 여부를 바꿔야 한다. */
	private static final class StubVendor implements TranslationVendorPort {

		boolean shouldFail = false;

		TranslationDirection lastDirection;

		@Override
		public String translate(String sourceText, TranslationDirection direction) {
			this.lastDirection = direction;
			if (this.shouldFail) {
				throw new TranslationVendorException("TRANSLATE_VENDOR_UNAVAILABLE", "번역 업체 호출에 실패했습니다.",
						HttpStatus.BAD_GATEWAY);
			}
			return "hello";
		}

		@Override
		public String providerName() {
			return "STUB_VENDOR";
		}
	}

	private static final class InMemoryCacheRepository implements TranslationCacheRepository {

		private final Map<String, String> store = new HashMap<>();

		@Override
		public Optional<String> findFreshTranslation(String sourceHash, Instant now) {
			return Optional.ofNullable(this.store.get(sourceHash));
		}

		@Override
		public void save(String sourceHash, String translatedText, Instant now, Instant expiresAt) {
			this.store.put(sourceHash, translatedText);
		}
	}
}
