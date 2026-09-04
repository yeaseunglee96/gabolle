import { apiRequest } from '@/api/client';

export type Festival = {
  placeId: string;
  title: string;
  titleEn: string | null;
  address: string;
  startDate: string;
  endDate: string;
  imageUrl: string | null;
  admissionFee?: string | null;
  localScore?: number | null;
};

type FestivalResponse = { festivals: Festival[] };

export async function getFestivals(from: string, to: string, signal?: AbortSignal) {
  const query = new URLSearchParams({ from, to, region: 'BUSAN' });
  const response = await apiRequest<FestivalResponse>(`/api/v1/festivals?${query.toString()}`, { signal });
  return Array.isArray(response.festivals) ? response.festivals : [];
}
