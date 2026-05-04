<script setup lang="ts">
import { RouterLink, RouterView } from 'vue-router'
</script>

<template>
  <div class="shell">
    <header class="top">
      <RouterLink to="/" class="brand">青鹿直播</RouterLink>
      <nav class="nav">
        <RouterLink to="/">概览</RouterLink>
        <RouterLink to="/studio">直播控制台</RouterLink>
        <RouterLink to="/products">商品</RouterLink>
      </nav>
    </header>
    <main class="main">
      <RouterView />
    </main>
  </div>
</template>

<style scoped>
.shell {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  background: var(--bg);
  color: var(--text);
}

.top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
  padding: 0.75rem 1.25rem;
  border-bottom: 1px solid var(--border);
  background: var(--surface);
}

.brand {
  font-weight: 700;
  letter-spacing: 0.02em;
  color: var(--accent);
  text-decoration: none;
}

.nav {
  display: flex;
  gap: 1rem;
}

.nav a {
  color: var(--muted);
  text-decoration: none;
  font-size: 0.95rem;
}

.nav a.router-link-active {
  color: var(--text);
  font-weight: 600;
}

.main {
  flex: 1;
  padding: 1.25rem;
  max-width: 1100px;
  width: 100%;
  margin: 0 auto;
}
</style>
