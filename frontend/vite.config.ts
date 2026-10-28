import { defineConfig } from "vite";
import vue from "@vitejs/plugin-vue";
export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    strictPort: true,
    proxy: { "/api": { target: "http://127.0.0.1:8080", changeOrigin: true } },
  },
  build: {
    license: { fileName: "third-party-licenses.md" },
    chunkSizeWarningLimit: 1500,
    rollupOptions: {
      output: {
        manualChunks(id) {
          // Group only modules already imported by the app; listing "echarts"
          // as an entry here forces its complete bundle into the build.
          if (/node_modules\/(echarts|zrender|vue-echarts)\//.test(id)) return "charts";
          if (/node_modules\/(element-plus|@element-plus)\//.test(id)) return "element";
          if (/node_modules\/(@vue|vue|vue-router)\//.test(id)) return "vue";
        },
      },
    },
  },
});
