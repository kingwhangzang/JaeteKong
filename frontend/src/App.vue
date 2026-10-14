<script setup lang="ts">
import { ref } from 'vue'
import HomeView from './views/HomeView.vue'
const active = ref('dashboard')
</script>

<template>
  <div class="app-shell">
    <header class="sidebar topbar">
      <button class="brand brand-button" @click="active = 'dashboard'"><span class="brand-mark">🌱</span><span><strong>재테콩</strong><small>차곡차곡 자라는 돈</small></span></button>
      <nav>
        <button :class="{ active: active === 'dashboard' }" @click="active = 'dashboard'">⌂ <span>한눈에 보기</span></button>
        <button :class="{ active: active === 'ledger' }" @click="active = 'ledger'">▣ <span>월별 가계부</span></button>
        <button :class="{ active: active === 'saving' }" @click="active = 'saving'">♧ <span>모아보기</span></button>
        <button :class="{ active: active === 'settings' }" @click="active = 'settings'">⚙ <span>설정</span></button>
      </nav>
      <div class="sidebar-note">작은 기록이 <b>큰 자산</b>이 돼요.</div>
    </header>
    <main class="main-content"><HomeView :active-tab="active" /></main>
  </div>
</template>
