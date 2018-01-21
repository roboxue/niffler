<template>
    <div class="container-fluid">
        <div class="row">
            <div class="col-md-3 bg-secondary px-1"
                 id="sidebar">
                <div class="card">
                    <div class="card-header" id="headingLiveExecutions">
                        <h5 class="mb-0 d-flex justify-content-between align-items-center"
                            data-target="#liveExecutionsList"
                            aria-expanded="true"
                            aria-controls="liveExecutionsList"
                            data-toggle="collapse">
                            Live executions
                            <span class="badge badge-primary badge-pill">{{liveExecutions.length}}</span>
                        </h5>
                    </div>
                    <div id="liveExecutionsList"
                         class="collapse show"
                         aria-labelledby="headingLiveExecutions"
                         data-parent="#sidebar">
                        <div class="list-group">
                            <history-execution
                                    v-for="(exe) in liveExecutions"
                                    v-on:view="viewExecution"
                                    :model="exe"
                                    :key="exe.executionId"
                            >
                            </history-execution>
                        </div>
                    </div>
                </div>
                <div class="card">
                    <div class="card-header" id="headingPastExecutions">
                        <h5 class="mb-0 d-flex justify-content-between align-items-center"
                            data-target="#pastExecutionsList"
                            aria-expanded="true"
                            aria-controls="pastExecutionsList"
                            data-toggle="collapse">
                            Past executions
                            <span class="badge badge-primary badge-pill">{{pastExecutions.length}}</span>
                        </h5>
                    </div>
                    <div id="pastExecutionsList"
                         class="collapse show"
                         aria-labelledby="headingPastExecutions"
                         data-parent="#sidebar">
                        <p class="list-group-item disabled">Storage capacity: {{capacity}}</p>
                        <div class="list-group">
                            <history-execution
                                    v-for="(exe) in pastExecutions"
                                    v-on:view="viewExecution"
                                    :model="exe"
                                    :key="exe.executionId"
                            >
                            </history-execution>
                        </div>
                    </div>
                </div>
            </div>
            <div class="col-md-9">
                <logic-dag v-if="activeExecution"
                           :model="activeExecution"
                           :live="activeExecution.state === 'live'"
                />
            </div>
        </div>
    </div>
</template>

<script>
  Vue.component('execution-history-service', {
    template: template,
    data: function () {
      return {
        loading: false,
        activeExecution: undefined,
        liveExecutions: [],
        pastExecutions: [],
        remainingCapacity: 1,
        // network error display
        alertVisible: false,
        errorMessage: {}
      }
    },
    computed: {
      capacity: function () {
        return this.remainingCapacity + this.pastExecutions.length
      },
      executionLookup: function () {
        let lookup = {}
        this.liveExecutions.forEach(exe => {
          lookup[exe.executionId] = exe
        })
        this.pastExecutions.forEach(exe => {
          lookup[exe.executionId] = exe
        })
        return lookup
      }
    },
    methods: {
      viewExecution: function (executionId) {
        let exe = this.executionLookup[executionId]
        if (exe) {
          this.loadSingleExecutionDag(executionId)
        }
      },
      loadExecutionHistory: function () {
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
      },
      loadSingleExecutionDag: function (executionId) {
        let vm = this
        axios.get(window.location.pathname + `/api/execution/${executionId}`)
          .then(function (response) {
            vm.activeExecution = response.data
            if (vm.activeExecution.state === 'live') {
              setTimeout(() => {
                if (vm.activeExecution && vm.activeExecution.executionId === executionId) {
                  vm.loadSingleExecutionDag(executionId)
                }
              }, 300)
            } else {
              vm.loadExecutionHistory()
            }
          })
          .catch(function (error) {
            vm.activeExecution = undefined
            errorHandling(error, vm, 'get execution dag')
          })
      }
    },
    mounted: function () {
      this.loadExecutionHistory()
    }
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

</script>

<style scoped>

</style>