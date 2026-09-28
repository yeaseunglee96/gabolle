"""보정 작업 하나를 돌린다 (S15P21E201-1692).

    python run.py stay_minutes            # 계산하고 결과 표에 새 판을 쌓는다
    python run.py stay_minutes --dry-run  # 계산과 검사만 하고 쓰지 않는다

DB 는 환경변수로 받는다: CALIBRATION_DB_URL(jdbc:postgresql://…) · CALIBRATION_DB_USER · CALIBRATION_DB_PASSWORD
(· CALIBRATION_DB_SCHEMA, 기본 gabolle). 종료 코드 — 0 끝남(보류가 있어도) · 2 설정 틀림 · 그 밖 계산 실패.
"""
import argparse
import sys

from framework import run

if __name__ == '__main__':
    parser = argparse.ArgumentParser(description='보정 작업 하나를 돌린다')
    parser.add_argument('job', help='jobs/ 아래 폴더 이름 (예: stay_minutes)')
    parser.add_argument('--dry-run', action='store_true', help='계산과 검사만 하고 결과 표에 쓰지 않는다')
    args = parser.parse_args()
    sys.exit(run(args.job, args.dry_run))
