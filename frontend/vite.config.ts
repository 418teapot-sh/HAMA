import tailwindcss from '@tailwindcss/vite'
import react from '@vitejs/plugin-react'
import { defineConfig, loadEnv } from 'vite'

// https://vite.dev/config/
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  /** 개발 서버가 /api 요청을 넘겨줄 백엔드. 로컬 백엔드를 띄웠다면 .env.local 에 http://localhost:8080 으로 바꾸세요. */
  const proxyTarget = env.VITE_API_PROXY_TARGET || 'https://api.todohama.site'

  return {
    plugins: [react(), tailwindcss()],
    server: {
      /**
       * npm run dev 에서는 브라우저가 localhost:5173/api 로만 요청하고, Vite 가 백엔드로 대신 전달합니다.
       * 브라우저 입장에서는 같은 주소라 CORS 가 필요 없고, refreshToken 쿠키도 정상으로 오갑니다.
       * (배포 서버를 바로 부르면 쿠키가 안 넘어가 30분마다 다시 로그인해야 합니다)
       */
      proxy: {
        '/api': {
          target: proxyTarget,
          changeOrigin: true,
          configure: (proxy) => {
            // 백엔드 CORS 허용 목록에 localhost:5173 이 없어서, 브라우저가 붙인 Origin 헤더를 떼고 보냅니다.
            proxy.on('proxyReq', (proxyReq) => proxyReq.removeHeader('origin'))
          },
        },
      },
    },
  }
})
