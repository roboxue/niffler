<template>
    <div class="container-fluid">
        <div class="jumbotron">
            <div class="row">
                <div class="col-md-6">
                    <h1 class="display-3">Execution History</h1>
                    <h4 v-if="loading">Loading...<br>
                        This tab will display the live executions and a portion of past execution history.
                    </h4>
                    <template v-else>
                        <h3>{{liveExecutions.length}} Live executions</h3>
                        <h5 v-if="liveExecutions.length === 0">empty</h5>
                        <history-execution
                                v-for="(exe) in liveExecutions"
                                v-on:view="viewExecution"
                                :model="exe"
                                :key="exe.executionId"
                        >
                        </history-execution>
                        <h3>{{pastExecutions.length}} Past executions</h3>
                        <h5>Storage capacity: {{capacity}}</h5>
                        <h5 v-if="pastExecutions.length === 0">empty</h5>
                        <history-execution
                                v-for="(exe) in pastExecutions"
                                v-on:view="viewExecution"
                                :model="exe"
                                :key="exe.executionId"
                        >
                        </history-execution>
                    </template>
                </div>
                <div class="col-md-6">
                    <h1 class="display-3">Execution Details</h1>
                    <logic-topology v-if="topology.length > 0"
                                    :model="topology"
                    ></logic-topology>
                </div>
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
        topology: [],
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
      }
    },
    methods: {
      viewExecution: function (executionId) {
        this.loadSingleExecutionTopology(executionId)
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
      loadSingleExecutionTopology: function (executionId) {
        let vm = this
        axios.get(window.location.pathname + `/api/topology/${executionId}`)
          .then(function (response) {
            vm.topology = response.data.topology
          })
          .catch(function (error) {
            vm.topology = []
            errorHandling(error, vm, 'get execution topology')
          })
      }
    },
    mounted: function () {
      this.loadExecutionHistory()
    }
  })
</script>

<style scoped>

</style>