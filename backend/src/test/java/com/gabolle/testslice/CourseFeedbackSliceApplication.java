package com.gabolle.testslice;

import org.springframework.boot.autoconfigure.SpringBootApplication;

/** 코스별 품질 피드백(S15P21E201-2020)을 진짜 PostgreSQL 위에서 보는 슬라이스. */
@SpringBootApplication(scanBasePackages = { "com.gabolle.backend.common", "com.gabolle.backend.coursefeedback" })
public class CourseFeedbackSliceApplication {
}
