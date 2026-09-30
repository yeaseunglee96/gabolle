package com.gabolle.backend.help.domain;

/**
 * 가까운 도움의 갈래 — 긴급 도움 화면의 탭 셋과 같다(S15P21E201-1893).
 */
public enum HelpKind {
	/** 병원·의원·보건소(심평원). 요양·정신병원, 치과, 한의원, 피부·성형·미용 의원은 자료에 없다 */
	HOSPITAL,
	/** 약국(심평원) */
	PHARMACY,
	/** 경찰서·지구대·치안센터(OSM) — 심평원 자료에 경찰은 없다 */
	POLICE
}
