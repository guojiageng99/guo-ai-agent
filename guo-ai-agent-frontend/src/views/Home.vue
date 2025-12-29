<template>
  <div class="home">
    <div class="container">
      <h1 class="title">AI超级智能体</h1>
      <p class="subtitle">选择您想要使用的AI应用</p>
      
      <div class="app-grid">
        <div class="app-card" @click="goToLoveApp">
          <div class="app-icon">💕</div>
          <h2 class="app-title">AI恋爱大师</h2>
          <p class="app-desc">专业的恋爱咨询助手，为您提供情感建议</p>
        </div>
        
        <div class="app-card" @click="goToManusApp">
          <div class="app-icon">🤖</div>
          <h2 class="app-title">AI超级智能体</h2>
          <p class="app-desc">强大的智能助手，解答各种问题</p>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { useRouter } from 'vue-router'

const router = useRouter()

const goToLoveApp = () => {
  router.push('/love-app')
}

const goToManusApp = () => {
  router.push('/manus-app')
}
</script>

<style scoped>
.home {
  width: 100%;
  height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
}

.container {
  text-align: center;
  padding: 40px;
  max-width: 1200px;
  width: 100%;
}

.title {
  font-size: 48px;
  color: #fff;
  margin-bottom: 10px;
  font-weight: 700;
}

.subtitle {
  font-size: 20px;
  color: rgba(255, 255, 255, 0.9);
  margin-bottom: 60px;
}

.app-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));
  gap: 30px;
  margin-top: 40px;
}

.app-card {
  background: #fff;
  border-radius: 16px;
  padding: 40px 30px;
  cursor: pointer;
  transition: all 0.3s ease;
  box-shadow: 0 10px 30px rgba(0, 0, 0, 0.2);
}

.app-card:hover {
  transform: translateY(-10px);
  box-shadow: 0 15px 40px rgba(0, 0, 0, 0.3);
}

.app-icon {
  font-size: 64px;
  margin-bottom: 20px;
}

.app-title {
  font-size: 24px;
  color: #333;
  margin-bottom: 15px;
  font-weight: 600;
}

.app-desc {
  font-size: 16px;
  color: #666;
  line-height: 1.6;
}
</style>

