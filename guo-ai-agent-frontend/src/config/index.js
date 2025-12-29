// 后端服务配置
// 开发环境使用完整地址，生产环境使用相对路径（通过nginx代理）
const isDevelopment = import.meta.env.DEV
export const API_BASE_URL = isDevelopment 
  ? 'http://localhost:8123/api'  // 开发环境
  : '/api'  // 生产环境（通过nginx代理）

// API接口路径
export const API_ENDPOINTS = {
  LOVE_APP_CHAT: '/ai/love_app/chat/sse',
  MANUS_CHAT: '/ai/manus/chat'
}

