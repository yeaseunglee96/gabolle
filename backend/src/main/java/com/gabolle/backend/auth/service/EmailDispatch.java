package com.gabolle.backend.auth.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

final class EmailDispatch {

	private static final Logger log = LoggerFactory.getLogger(EmailDispatch.class);

	private EmailDispatch() {
	}

	static void afterCommit(Runnable dispatch) {
		if (!TransactionSynchronizationManager.isSynchronizationActive()) {
			safeRun(dispatch);
			return;
		}
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCommit() {
				safeRun(dispatch);
			}
		});
	}

	private static void safeRun(Runnable dispatch) {
		try {
			dispatch.run();
		} catch (RuntimeException exception) {
			log.error("인증 메일 발송에 실패했습니다. 재발송 API로 재시도할 수 있습니다.", exception);
		}
	}
}
