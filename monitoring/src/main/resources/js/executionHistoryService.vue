<template>
    <div class="container-fluid">
        <div class="row">
            <div class="col-md-6">
                <h1 class="display-3">Execution History</h1>
                <div class="card">
                    <div class="card-body" v-if="loading">
                        <h4 class="card-title">Loading...<br>
                            This tab will display the live executions and a portion of past execution history.
                        </h4>
                    </div>
                    <template v-else>
                        <div class="card-header">
                            <span class="badge badge-secondary">{{liveExecutions.length}}</span> Live executions
                        </div>
                        <div class="card-body" v-show="liveExecutions.length === 0">
                            <p class="card-text">empty</p>
                        </div>
                        <history-execution
                                v-for="(exe) in liveExecutions"
                                v-on:view="viewExecution"
                                :model="exe"
                                :key="exe.executionId"
                        >
                        </history-execution>
                        <div class="card-header">
                            <span class="badge badge-secondary">{{pastExecutions.length}}</span> Past executions
                        </div>
                        <div class="card-body">
                            <p class="card-subtitle text-muted">Storage capacity: {{capacity}}</p>
                            <p class="card-text" v-show="pastExecutions.length === 0">empty</p>
                        </div>
                        <history-execution
                                v-for="(exe) in pastExecutions"
                                v-on:view="viewExecution"
                                :model="exe"
                                :key="exe.executionId"
                        >
                        </history-execution>
                    </template>
                </div>
            </div>
            <div class="col-md-6">
                <h1 class="display-3">Execution Details
                    <span v-if="activeExecution">for #{{activeExecution.executionId}}</span>
                </h1>
                <h3 class="text-muted" v-if="activeExecution">
                    <i class="fa fa-spinner fa-spin" v-show="socket && socket.readyState === 1"></i>
                    As of {{new Date(activeExecution.asOfTime).toISOString()}} ({{activeExecution.asOfTime}})
                </h3>
                <logic-topology v-if="activeExecution"
                                :model="activeExecution"
                ></logic-topology>
            </div>
        </div>
    </div>
</template>

<script>
  Vue.component('execution-history-service', {
    template: template,
    data: function () {
      return {
        socket: undefined,
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
          if (exe.state === 'live') {
            this.loadSingleExecutionStream(executionId)
          } else {
            this.loadSingleExecutionTopology(executionId)
          }
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
      loadSingleExecutionStream: function (executionId) {
        let vm = this
        if (vm.socket) {
          vm.socket.onclose = null
          vm.socket.close()
        }
        let newSocket = new WebSocket(`ws://${window.location.host}${window.location.pathname}/api/executionStream/${executionId}`)
        newSocket.addEventListener('message', function (event) {
          vm.activeExecution = JSON.parse(event.data)
        })
        newSocket.addEventListener('close', function () {
          vm.loadExecutionHistory()
          vm.loadSingleExecutionTopology(executionId)
          vm.socket = null
        })
        vm.socket = newSocket
      },
      loadSingleExecutionTopology: function (executionId) {
        let vm = this
        axios.get(window.location.pathname + `/api/execution/${executionId}`)
          .then(function (response) {
            vm.activeExecution = response.data
          })
          .catch(function (error) {
            vm.activeExecution = undefined
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