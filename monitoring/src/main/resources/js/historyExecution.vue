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
            <dl class="row">
                <dt class="col-sm-3">Token to eval</dt>
                <dd class="col-sm-9">{{model.token}}</dd>
                <dt class="col-sm-3">Return type</dt>
                <dd class="col-sm-9"><code>{{model.tokenType}}</code></dd>
                <template v-if="model.state === 'success' || model.state === 'failure'">
                    <dt class="col-sm-3">Finished at</dt>
                    <dd class="col-sm-9">
                        <abbr :title="new Date(model.finishAt).toISOString()">{{model.finishAt}}</abbr>
                        ({{model.finishAt - model.startAt}} millis)
                    </dd>
                </template>
                <template v-if="model.state === 'failure'">
                    <dt class="col-sm-3">Exception</dt>
                    <dd class="col-sm-9">{{model.exceptionMessage}}</dd>
                    <dt class="col-sm-3">Stacktrace</dt>
                    <dd class="col-sm-9">
                        <pre style="white-space: pre-wrap; -ms-word-wrap: break-word;word-wrap: break-word;">
                            {{model.stacktrace}}
                        </pre>
                    </dd>

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