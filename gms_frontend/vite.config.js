import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  preview: {
    // `vite preview` stands in for the reverse proxy in the Playwright job, and this is the
    // part it has to stand in for. The built bundle asks for `/api/v1` (the production
    // branch of `getApiBaseUrl()`), so without this the SPA would be served from 4173 and
    // fetch from 4173, where no API lives. Forwarding `/api` server-side keeps the browser's
    // requests same-origin, which is what lets the suite run against a backend that refuses
    // to name `localhost` in `cors.allowed-origins` rather than against a loosened one.
    proxy: {
      '/api': {
        target: process.env.GMS_E2E_API_ORIGIN ?? 'http://localhost:8080',
        changeOrigin: true,
        // Chrome attaches `Origin` to every POST, including one from a page to its own origin, and
        // Spring's CorsFilter treats any request carrying an Origin that is not in
        // `cors.allowed-origins` as cross-origin and answers 403 `Invalid CORS request` — which is
        // exactly what the first run of this suite measured (`❌ Login error: Invalid CORS request`
        // in the trace). The backend cannot be told to allow this page's origin: that list is
        // `http://localhost:<port>`, and `StartupSecurityValidator` refuses to boot with a
        // `localhost` entry outside the dev profile, which the harness deliberately does not set.
        // So the proxy drops the header and the request reaches the API as the same-origin call it
        // genuinely is — the same shape as the fixture's own server-side posts, which the API
        // accepts. What this does NOT do is test the CORS filter; `e2e/admin-journey.spec.js`
        // covers that from the other side, by sending a request whose origin really is refused.
        configure: (proxy) => {
          proxy.on('proxyReq', (proxyReq) => proxyReq.removeHeader('origin'));
        },
      },
    },
  },
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: './src/test/setup.js',
    include: ['src/**/*.{test,spec}.{js,jsx}'],
  },
})
