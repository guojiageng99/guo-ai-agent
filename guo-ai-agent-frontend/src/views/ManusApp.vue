<template>
  <ChatRoom
    title="AI超级智能体"
    :api-url="apiUrl"
    :get-params="getParams"
  />
</template>

<script setup>
import { computed } from 'vue'
import ChatRoom from '../components/ChatRoom.vue'
import { API_BASE_URL, API_ENDPOINTS } from '../config'

const apiUrl = computed(() => `${API_BASE_URL}${API_ENDPOINTS.MANUS_CHAT}`)

// 获取请求参数
const getParams = (message) => {
  return {
    message: message
  }
}
</script>

