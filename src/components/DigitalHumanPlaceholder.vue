<script setup lang="ts">
/**
 * Live2D Cubism 占位：在此挂载 canvas 并加载 model3.json（SDK 需单独授权）。
 */
</script>

<template>
  <div class="dh" role="region" aria-label="数字人渲染区">
    <div class="badge">输出层 · Live2D 数字人驱动</div>
    <p class="title">Cubism SDK 挂载区</p>
    <p class="sub">
      TTS 语音合成模块输出音频后，在此驱动口型与表情（参见 <code>src/modules/live2d</code>）。
    </p>
  </div>
</template>

<style scoped>
.dh {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 200px;
  padding: 1.25rem;
  text-align: center;
  border: 1px dashed rgba(94, 200, 255, 0.35);
  border-radius: 16px;
  background: rgba(12, 16, 23, 0.85);
}

.badge {
  font-size: 0.72rem;
  padding: 0.2rem 0.55rem;
  border-radius: 999px;
  background: rgba(94, 200, 255, 0.12);
  color: var(--accent);
  margin-bottom: 0.75rem;
}

.title {
  margin: 0 0 0.5rem;
  font-size: 1.05rem;
  font-weight: 700;
}

.sub {
  margin: 0;
  font-size: 0.88rem;
  color: var(--muted);
  max-width: 420px;
  line-height: 1.45;
}

code {
  font-size: 0.82rem;
}
</style>
