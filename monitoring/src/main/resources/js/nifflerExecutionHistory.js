// noinspection JSUnusedGlobalSymbols
requirejs.config({
  paths: {
    "Vue": "https://cdnjs.cloudflare.com/ajax/libs/vue/2.5.13/vue.min",
    "vue": "https://rawgit.com/edgardleal/require-vuejs/master/dist/require-vuejs"
  },
  shim: {
    "Vue": {"exports": "Vue"}
  }
})

requirejs(["vue!historyExecution"], function(HistoryExecutionComponent) {
  const app = new Vue({
    el: '#app',
    data: {
      loading: false,
      liveExecutions: [],
      pastExecutions: [],
      remainingCapacity: 1,
      // network error display
      alertVisible: false,
      errorMessage: {}
    },
    computed: {
      capacity: function() {
        return this.remainingCapacity + this.pastExecutions.length
      }
    },
    methods: {
      viewExecution: function(executionId) {
        console.info(executionId)
      },
      loadLogReaders: function (event) {
        let vm = this
        vm.loading = true
        axios.get(window.location.pathname + '/api/status')
          .then(function (response) {
            vm.loading = false
            vm.liveExecutions = response.data.liveExecutions
            vm.pastExecutions = response.data.pastExecutions
            vm.remainingCapacity = response.data.remainingCapacity
          })
          .catch(function (error) {
            vm.loading = false
            errorHandling(error, vm, 'get execution history')
          })
      }
    },
    mounted: function () {
      this.loadLogReaders()
    }
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
