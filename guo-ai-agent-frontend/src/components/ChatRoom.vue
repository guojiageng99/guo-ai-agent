<template>
  <div class="chat-room">
    <div class="chat-header">
      <button class="back-btn" @click="goBack">
        ← 返回
      </button>
      <h2 class="chat-title">{{ title }}</h2>
    </div>
    
    <div class="chat-messages" ref="messagesContainer">
      <div
        v-for="(message, index) in messages"
        :key="index"
        :class="['message', message.role]"
      >
        <div class="message-content">
          <div class="message-text">{{ message.content }}</div>
          <div class="message-time">{{ message.time }}</div>
        </div>
      </div>
      
      <div v-if="isLoading" class="message ai">
        <div class="message-content">
          <div class="message-text typing">AI正在思考...</div>
        </div>
      </div>
    </div>
    
    <div class="chat-input-area">
      <div class="input-wrapper">
        <textarea
          v-model="inputMessage"
          @keydown.enter.exact.prevent="sendMessage"
          @keydown.shift.enter.exact="inputMessage += '\n'"
          placeholder="输入消息..."
          class="chat-input"
          rows="1"
          ref="inputRef"
        ></textarea>
        <button
          @click="sendMessage"
          :disabled="!inputMessage.trim() || isLoading"
          class="send-btn"
        >
          发送
        </button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, nextTick, onUnmounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { createSSEConnection, closeSSEConnection } from '../utils/sse'

const props = defineProps({
  title: {
    type: String,
    required: true
  },
  apiUrl: {
    type: String,
    required: true
  },
  getParams: {
    type: Function,
    required: true
  }
})

const router = useRouter()
const messages = ref([])
const inputMessage = ref('')
const isLoading = ref(false)
const messagesContainer = ref(null)
const inputRef = ref(null)
let eventSource = null

// 格式化时间
const formatTime = () => {
  const now = new Date()
  return now.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
}

// 发送消息
const sendMessage = async () => {
  if (!inputMessage.value.trim() || isLoading.value) return
  
  const userMessage = {
    role: 'user',
    content: inputMessage.value.trim(),
    time: formatTime()
  }
  
  messages.value.push(userMessage)
  const messageToSend = inputMessage.value.trim()
  inputMessage.value = ''
  isLoading.value = true
  
  // 滚动到底部
  await nextTick()
  scrollToBottom()
  
  // 关闭之前的连接
  if (eventSource) {
    closeSSEConnection(eventSource)
  }
  
  // 获取请求参数
  const params = props.getParams(messageToSend)
  
  // 创建SSE连接
  let currentContent = ''
  let aiMessageIndex = -1
  const stopKeywords = ['等你回复', '等待你的回复', '等你回答', '等待您的回复']
  let shouldStop = false
  
  eventSource = createSSEConnection(
    props.apiUrl,
    params,
    (data) => {
      // 如果已经检测到停止条件，不再处理新数据
      if (shouldStop) {
        return
      }
      
      // 累积消息内容
      currentContent += data
      
      // 1. 检查是否包含停止关键词
      for (const keyword of stopKeywords) {
        if (currentContent.includes(keyword)) {
          shouldStop = true
          // 找到第一个停止关键词的位置，只保留到该位置的内容
          const keywordIndex = currentContent.indexOf(keyword)
          if (keywordIndex !== -1) {
            currentContent = currentContent.substring(0, keywordIndex + keyword.length)
          }
          // 关闭SSE连接
          if (eventSource) {
            closeSSEConnection(eventSource)
            eventSource = null
          }
          isLoading.value = false
          break
        }
      }
      
      // 2. 检查问号结尾（与后端逻辑保持一致）
      if (!shouldStop) {
        const lastQIndex = Math.max(
          currentContent.lastIndexOf('？'),
          currentContent.lastIndexOf('?')
        )
        if (lastQIndex !== -1) {
          const distanceFromEnd = currentContent.length - lastQIndex - 1
          // 问号在末尾20字内，且问号后内容少于10字
          if (distanceFromEnd <= 20) {
            const afterQuestion = currentContent.substring(lastQIndex + 1).trim()
            if (afterQuestion.length < 10) {
              shouldStop = true
              currentContent = currentContent.substring(0, lastQIndex + 1)
              if (eventSource) {
                closeSSEConnection(eventSource)
                eventSource = null
              }
              isLoading.value = false
            }
          }
        }
      }
      
      // 3. 检查内容长度（超过200字强制停止，与后端保持一致）
      if (!shouldStop && currentContent.length > 200) {
        shouldStop = true
        currentContent = currentContent.substring(0, 200)
        if (eventSource) {
          closeSSEConnection(eventSource)
          eventSource = null
        }
        isLoading.value = false
      }
      
      // 更新或添加AI消息
      if (aiMessageIndex === -1 || !messages.value[aiMessageIndex] || messages.value[aiMessageIndex].role !== 'ai') {
        // 创建新的AI消息
        messages.value.push({
          role: 'ai',
          content: currentContent,
          time: formatTime()
        })
        aiMessageIndex = messages.value.length - 1
      } else {
        // 更新现有AI消息
        messages.value[aiMessageIndex].content = currentContent
      }
      
      // 滚动到底部
      nextTick(() => {
        scrollToBottom()
      })
    },
    (error) => {
      console.error('SSE连接错误:', error)
      isLoading.value = false
      // 如果已经有AI消息，更新它；否则创建新的错误消息
      if (aiMessageIndex !== -1 && messages.value[aiMessageIndex]) {
        if (!messages.value[aiMessageIndex].content.trim()) {
          messages.value[aiMessageIndex].content = '抱歉，连接出现错误，请重试。'
        }
      } else {
        messages.value.push({
          role: 'ai',
          content: '抱歉，连接出现错误，请重试。',
          time: formatTime()
        })
      }
    },
    () => {
      // 连接打开
      console.log('SSE连接已建立')
    },
    () => {
      // 连接关闭
      isLoading.value = false
      console.log('SSE连接已关闭')
    }
  )
}

// 滚动到底部
const scrollToBottom = () => {
  if (messagesContainer.value) {
    messagesContainer.value.scrollTop = messagesContainer.value.scrollHeight
  }
}

// 返回主页
const goBack = () => {
  if (eventSource) {
    closeSSEConnection(eventSource)
  }
  router.push('/')
}

// 组件卸载时关闭连接
onUnmounted(() => {
  if (eventSource) {
    closeSSEConnection(eventSource)
  }
})

// 监听消息变化，自动滚动
watch(messages, () => {
  nextTick(() => {
    scrollToBottom()
  })
}, { deep: true })
</script>

<style scoped>
.chat-room {
  width: 100%;
  height: 100vh;
  display: flex;
  flex-direction: column;
  background: #f5f5f5;
}

.chat-header {
  background: #fff;
  padding: 15px 20px;
  border-bottom: 1px solid #e0e0e0;
  display: flex;
  align-items: center;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
}

.back-btn {
  background: none;
  border: none;
  color: #667eea;
  font-size: 16px;
  cursor: pointer;
  padding: 5px 10px;
  margin-right: 15px;
  border-radius: 4px;
  transition: background 0.2s;
}

.back-btn:hover {
  background: #f0f0f0;
}

.chat-title {
  font-size: 20px;
  color: #333;
  font-weight: 600;
}

.chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: 20px;
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.message {
  display: flex;
  animation: fadeIn 0.3s ease;
}

.message.user {
  justify-content: flex-end;
}

.message.ai {
  justify-content: flex-start;
}

.message-content {
  max-width: 70%;
  padding: 12px 16px;
  border-radius: 12px;
  word-wrap: break-word;
}

.message.user .message-content {
  background: #667eea;
  color: #fff;
  border-bottom-right-radius: 4px;
}

.message.ai .message-content {
  background: #fff;
  color: #333;
  border-bottom-left-radius: 4px;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
}

.message-text {
  font-size: 15px;
  line-height: 1.6;
  white-space: pre-wrap;
}

.message-time {
  font-size: 12px;
  margin-top: 5px;
  opacity: 0.7;
}

.message.user .message-time {
  text-align: right;
}

.typing {
  color: #999;
  font-style: italic;
}

.chat-input-area {
  background: #fff;
  padding: 15px 20px;
  border-top: 1px solid #e0e0e0;
}

.input-wrapper {
  display: flex;
  gap: 10px;
  align-items: flex-end;
  max-width: 1200px;
  margin: 0 auto;
}

.chat-input {
  flex: 1;
  border: 1px solid #e0e0e0;
  border-radius: 8px;
  padding: 12px 15px;
  font-size: 15px;
  resize: none;
  font-family: inherit;
  max-height: 120px;
  overflow-y: auto;
}

.chat-input:focus {
  outline: none;
  border-color: #667eea;
}

.send-btn {
  background: #667eea;
  color: #fff;
  border: none;
  border-radius: 8px;
  padding: 12px 24px;
  font-size: 15px;
  cursor: pointer;
  transition: background 0.2s;
  white-space: nowrap;
}

.send-btn:hover:not(:disabled) {
  background: #5568d3;
}

.send-btn:disabled {
  background: #ccc;
  cursor: not-allowed;
}

@keyframes fadeIn {
  from {
    opacity: 0;
    transform: translateY(10px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

/* 滚动条样式 */
.chat-messages::-webkit-scrollbar {
  width: 6px;
}

.chat-messages::-webkit-scrollbar-track {
  background: #f1f1f1;
}

.chat-messages::-webkit-scrollbar-thumb {
  background: #888;
  border-radius: 3px;
}

.chat-messages::-webkit-scrollbar-thumb:hover {
  background: #555;
}
</style>

