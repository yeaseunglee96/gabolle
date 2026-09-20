package com.gabolle.backend.batch.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 접는 규칙의 판 번호. 기본값을 두지 않는다 — 같은 잣대로 만든 벡터끼리만 비교할 수 있는데,
 * 못 구했을 때 임의의 값을 넣으면 그 벡터가 어느 규칙에서 나왔는지 영영 알 수 없고 표는
 * 멀쩡해 보인다. 비어 있으면 {@link TasteVectorFoldService} 가 요청을 실패시킨다.
 */
@ConfigurationProperties(prefix = "gabolle.taste-vector")
public class TasteVectorProperties {

	/** 어떻게 접었는가. 접는 산수를 고치면 이 값을 올린다. */
	private String vectorVersion = "";

	/** 어떤 개념 사전으로 접었는가. 차원·코드가 바뀌면 벡터의 뜻이 달라진다. */
	private String ontologyVersion = "";

	public String getVectorVersion() {
		return this.vectorVersion;
	}

	public void setVectorVersion(String vectorVersion) {
		this.vectorVersion = (vectorVersion == null) ? "" : vectorVersion.trim();
	}

	public String getOntologyVersion() {
		return this.ontologyVersion;
	}

	public void setOntologyVersion(String ontologyVersion) {
		this.ontologyVersion = (ontologyVersion == null) ? "" : ontologyVersion.trim();
	}
}
