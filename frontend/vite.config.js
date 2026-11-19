import react from '@vitejs/plugin-react';
import { defineConfig } from 'vite';

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],

  server: {
    // DEVELOPMENT ONLY: "npm run dev" serves the app on http://localhost:5173 and
    // forwards every request starting with /api to Spring Boot on port 8080.
    // To the browser, the frontend and the backend are then ONE site, so the session
    // and CSRF cookies just work, with no cross-site cookie problems.
    // (Production gets the same trick later, with Vercel "rewrites".)
    proxy: {
      '/api': 'http://localhost:8080',
    },
  },

  // Settings for the tests ("npm test"), which use Vitest.
  test: {
    // jsdom = a pretend browser inside Node, so components can be rendered in tests.
    environment: 'jsdom',
    // Runs before every test file (adds matchers like toBeInTheDocument()).
    setupFiles: './src/test/setup.js',
  },
});