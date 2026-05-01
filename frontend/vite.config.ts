import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
import tailwindcss from "@tailwindcss/vite";

const devProxyTarget = process.env.VITE_DEV_PROXY_TARGET ?? "http://localhost:8080";

export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    host: "0.0.0.0",
    port: 5173,
    proxy: {
      "/api": devProxyTarget,
      "/health": devProxyTarget,
    },
  },
  preview: {
    host: "0.0.0.0",
    port: 4173,
  },
});
