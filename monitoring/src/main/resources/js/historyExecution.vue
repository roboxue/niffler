<template>
    <div class="list-group">
        <a href="#"
           @click.prevent="viewExecution"
           class="list-group-item list-group-item-action flex-column align-items-start"
        >
            <div class="d-flex w-100 justify-content-between">
                <h5 class="mb-1">
                    <span class="badge" :class="['badge-' + colorForState(model.state)]">&nbsp;&nbsp;</span>
                    #{{model.executionId}} - {{model.state}}</h5>
                <abbr :title="new Date(model.startAt).toISOString()">{{model.startAt}}</abbr>
            </div>
            <dl class="row mb-0">
                <dt class="col-sm-3">Token</dt>
                <dd class="col-sm-9">{{model.token}}</dd>
                <template v-if="model.state === 'success' || model.state === 'failure'">
                    <dt class="col-sm-3">Finished</dt>
                    <dd class="col-sm-9">
                        <abbr :title="new Date(model.finishAt).toISOString()">{{model.finishAt}}</abbr>
                        ({{model.finishAt - model.startAt}} millis)
                    </dd>
                </template>
                <template v-if="model.state === 'failure'">
                    <dt class="col-sm-3">Exception</dt>
                    <dd class="col-sm-9">{{model.exceptionMessage}}</dd>
                </template>
            </dl>
        </a>
    </div>

</template>

<script>
  Vue.component('history-execution', {
    props: ['model'],
    methods: {
      colorForState: function (state) {
        switch (state) {
          case 'live':
            return 'warning'
          case 'cancelled':
            return 'info'
          case 'success':
            return 'success'
          case 'failure':
            return 'danger'
        }
      },
      viewExecution: function () {
        this.$emit('view', this.model.executionId)
      }
    },
    template: template
  })
</script>

<style scoped>

</style>