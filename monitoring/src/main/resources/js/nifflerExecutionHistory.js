requirejs.config({
  paths: {
    'Vue': 'https://cdnjs.cloudflare.com/ajax/libs/vue/2.5.13/vue.min',
    'vue': 'https://rawgit.com/edgardleal/require-vuejs/master/dist/require-vuejs'
  },
  shim: {
    'Vue': {'exports': 'Vue'}
  }
})

requirejs(['vue!executionHistoryService', 'vue!historyExecution', 'vue!logicTopology'], function () {
  const app = new Vue({
    el: '#app',
  })
})
