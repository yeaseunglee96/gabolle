import react from "@vitejs/plugin-react";
import { defineConfig } from "vite";

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    // 5173 이 이미 사용 중이면 조용히 5174 로 옮기지 않고 에러로 알린다.
    // (다른 포트로 옮겨 가면 localhost:5173 에는 아무것도 뜨지 않는다)
    strictPort: true,
    proxy: {
      "/api": "http://localhost:4000",
    },
  },
  optimizeDeps: {
    // 라우터는 첫 화면부터 필요하므로 사전 번들 대상에 명시한다.
    // 실행 중 새 의존성이 발견되어 재최적화 + 새로고침이 도는 것을 막는다.
    include: ["react-router-dom"],
  },
});
