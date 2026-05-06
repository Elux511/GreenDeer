<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import AppShell from './layouts/AppShell.vue'

const route = useRoute()
const withChrome = computed(() => route.meta.minimal !== true)
</script>

<template>
  <AppShell v-if="withChrome" />
  <RouterView v-else />
</template>
