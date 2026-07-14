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
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true
      }
    }
  },

  // 生产构建配置
  outputDir: 'dist',
  assetsDir: 'static',

  productionSourceMap: false,
  lintOnSave: false,

  // 配置 webpack
  configureWebpack: {
    resolve: {
      alias: {
        '@': require('path').resolve(__dirname, 'src')
      }
    }
  },

  // 修复 webpack 5 CopyPlugin 与 HtmlWebpackPlugin 对 index.html 的冲突
  chainWebpack: config => {
    config.plugin('copy').tap(args => {
      const options = args[0]
      if (options.patterns) {
        options.patterns.forEach(pattern => {
          if (!pattern.globOptions) pattern.globOptions = {}
          if (!pattern.globOptions.ignore) pattern.globOptions.ignore = []
          if (!pattern.globOptions.ignore.includes('**/index.html')) {
            pattern.globOptions.ignore.push('**/index.html')
          }
        })
      }
      return args
    })
  }
})
