<template>
  <ChatRoom
    title="AI恋爱大师"
    :api-url="apiUrl"
    :get-params="getParams"
  />
</template>

<script setup>
import { ref, computed } from 'vue'
import ChatRoom from '../components/ChatRoom.vue'
import { API_BASE_URL, API_ENDPOINTS } from '../config'

const apiUrl = computed(() => `${API_BASE_URL}${API_ENDPOINTS.LOVE_APP_CHAT}`)

// 生成唯一的聊天室ID
const generateChatId = () => {
  return `love_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`
}

const chatId = ref(generateChatId())

// 获取请求参数
const getParams = (message) => {
  return {
    message: message,
    chatId: chatId.value
  }
}
</script>

