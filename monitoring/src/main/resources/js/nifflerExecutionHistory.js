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

function errorHandling (error, vm, occasion) {
  if (error.response) {
    vm.errorMessage = {
      message: error.response.data,
      status: error.response.status,
      occasion: occasion
    }
  } else if (error.request) {
    vm.errorMessage = {
      message: 'no response received',
      status: 'n/a',
      occasion: occasion
    }
  } else {
    vm.errorMessage = {
      message: error.message,
      status: 'n/a',
      occasion: occasion
    }
  }
  vm.alertVisible = true
}
