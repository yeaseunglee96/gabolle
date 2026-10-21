package com.gabolle.testslice;

import org.springframework.boot.autoconfigure.SpringBootApplication;

/** 운영 지표(S15P21E201-2010)를 진짜 PostgreSQL 위에서 보는 슬라이스. */
@SpringBootApplication(scanBasePackages = { "com.gabolle.backend.common", "com.gabolle.backend.metrics" })
public class MetricsSliceApplication {
}
