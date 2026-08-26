import { useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { TripFormPage } from "./TripFormPage";
import { createTrip, getStoredAccount } from "../api/client";
import type { CreateTripRequest } from "../types";
import { useAppShell } from "../routes/AppShell";
import { paths } from "../routes/paths";

const STEP_SLUGS = ["basic", "taste", "confirm"] as const;
type StepSlug = (typeof STEP_SLUGS)[number];

/**
 * /plan/:step — 3단계 여행 조건 입력 위저드
 *
 * 단계를 URL 로 노출해 뒤로가기·새로고침·딥링크가 모두 동작하게 만든다.
 * 앱에서도 같은 3개 화면을 스택 네비게이션으로 그대로 재현한다.
 */
export function PlanWizardPage() {
  const { step: stepParam } = useParams();
  const navigate = useNavigate();
  const { placeCount, lodgings } = useAppShell();
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const account = getStoredAccount();

  const stepIndex = Math.max(0, STEP_SLUGS.indexOf((stepParam ?? "basic") as StepSlug));

  const handleSubmit = async (payload: CreateTripRequest) => {
    setSubmitting(true);
    setErrorMessage(null);
    try {
      const { tripId } = await createTrip(payload);
      navigate(paths.generating(tripId), { state: { mode: payload.recommendationMode } });
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : "일정을 만들지 못했습니다.");
      setSubmitting(false);
    }
  };

  return (
    <TripFormPage
      onSubmit={handleSubmit}
      submitting={submitting}
      errorMessage={errorMessage}
      placeCount={placeCount}
      lodgings={lodgings}
      initialValues={account ? {
        language: account.locale,
        dietType: account.dietType,
        allergies: account.allergies,
        pace: account.travelStyle === "BALANCED" ? "NORMAL" : account.travelStyle,
        hasCar: account.defaultTransport === "CAR",
      } : undefined}
      step={stepIndex}
      onStepChange={(next) => navigate(paths.plan(STEP_SLUGS[next] ?? "basic"), { replace: true })}
    />
  );
}
