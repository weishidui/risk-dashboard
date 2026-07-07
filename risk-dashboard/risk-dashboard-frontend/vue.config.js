/**
 * Vue CLI 配置文件
 * 金融交易风险实时监控平台 - 可视化大屏
 */
const { defineConfig } = require('@vue/cli-service')

module.exports = defineConfig({
  // 开发服务器配置
  devServer: {
    port: 8081,
    proxy: {
      // 代理后端 API 请求 (解决跨域)
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true
      }
    }
  },

  // 生产构建配置
  outputDir: 'dist',
  assetsDir: 'static',

  // 不生成 source map (生产环境)
  productionSourceMap: false,

  // 关闭 LintOnSave (可在开发时开启)
  lintOnSave: false,

  // 配置 webpack
  configureWebpack: {
    resolve: {
      alias: {
        '@': require('path').resolve(__dirname, 'src')
      }
    }
  }
})
