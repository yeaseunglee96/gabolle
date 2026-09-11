// 화면 번호·섹션 라벨 한 줄 (예: "06 · 여행 만들기", "08 · 접근성").
// 08 화면의 눈썹 문구가 text.eyebrow 색이라 그 이름을 붙였다.
import { Text } from './Text';

type EyebrowProps = {
  children: string;
};

export function Eyebrow({ children }: EyebrowProps) {
  return (
    <Text variant="eyebrow" weight="bold">
      {children}
    </Text>
  );
}
