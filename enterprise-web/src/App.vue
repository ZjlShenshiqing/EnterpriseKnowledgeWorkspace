<template>
  <router-view v-slot="{ Component }">
    <transition name="view-fade" mode="out-in">
      <component :is="Component" />
    </transition>
  </router-view>
</template>

<style>
.view-fade-enter-active { transition: opacity .25s ease, transform .25s ease; }
.view-fade-leave-active { transition: opacity .15s ease; }
.view-fade-enter-from { opacity:0; transform: translateY(6px); }
.view-fade-leave-to { opacity:0; }
</style>
