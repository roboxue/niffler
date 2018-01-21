<template>
    <div class="row">
        <div class="col-12 d-flex justify-content-between">
            <h3>Execution Details
                <span v-if="model">for #{{model.executionId}}</span>
            </h3>
            <h3 class="text-muted" v-if="model">
                <i class="fa fa-spinner fa-spin" v-show="live"></i>
                As of {{prettyPrintTime(model.asOfTime)}} ({{model.asOfTime}})
            </h3>
        </div>
        <div class="col-4">
            <div class="card mb-1">
                <div class="card-header">Token View</div>
                <div class="card-body" v-if="activeToken !== undefined">
                    <!--metadata-->
                    <h3 class="card-title">{{activeToken.codeName}}</h3>
                    <h5>Status:
                        <span :class="`text-${colorForTimelineEvent(activeToken.executionStatus)}`">
                            {{activeToken.executionStatus}}
                        </span>
                    </h5>
                    <h5>Return Type:</h5>
                    <p>{{activeToken.returnType}}</p>
                    <h5>Doc string:</h5>
                    <p>{{activeToken.name}}</p>
                    <!--prerequisites-->
                    <h5>Prerequisites:</h5>
                    <ul class="nav flex-column">
                        <li class="nav-item"
                            v-for="uuid in activeToken.prerequisites"
                            :key="uuid">
                            <a href="#"
                               @click.prevent="viewToken(uuid)"
                               class="nav-link">
                                {{tokenLookupTable[uuid].codeName}}
                            </a>
                        </li>
                        <li class="nav-item" v-if="activeToken.prerequisites.length === 0">
                            <a class="nav-link disabled" href="#">Leaf token</a>
                        </li>
                    </ul>
                    <!--successors-->
                    <h5>Unblocks:</h5>
                    <ul class="nav flex-column">
                        <li class="nav-item"
                            v-for="uuid in activeToken.successors"
                            :key="uuid">
                            <a href="#"
                               class="nav-link"
                               @click.prevent="viewToken(uuid)">
                                {{tokenLookupTable[uuid].codeName}}
                            </a>
                        </li>
                        <li class="nav-item" v-if="activeToken.successors.length === 0">
                            <a class="nav-link disabled" href="#">Root token</a>
                        </li>
                    </ul>
                    <!--token timeline-->
                    <h5>Timeline:</h5>
                    <p v-for="event in timelineEventsForToken(activeToken.uuid)" class="d-flex justify-content-between">
                        <strong class="text-capitalize" :class="`text-${colorForTimelineEvent(event.eventType)}`">
                            {{event.eventType}}:
                        </strong>
                        <span>
                            {{prettyPrintTime(event.time)}} (<mark>{{event.time}}</mark>)
                        </span>
                    </p>
                    <p v-if="timelineEventsForToken(activeToken.uuid).length === 0">
                        No info
                    </p>
                    <p v-if="activeToken.hasOwnProperty('startTime') && activeToken.hasOwnProperty('completeTime')"
                       class="d-flex justify-content-between">
                        <strong>Duration:</strong>
                        <span>{{activeToken.completeTime - activeToken.startTime}} ms</span>
                    </p>
                </div>
                <div class="card-body" v-else>
                    <p class="card-text">Select a token to view details</p>
                </div>
            </div>
            <div class="card">
                <div class="card-header">Timeline</div>
                <table class="table table-sm table-responsive">
                    <thead>
                    <tr>
                        <th scope="col">Time</th>
                        <th scope="col">Event</th>
                        <th scope="col">Token</th>
                    </tr>
                    </thead>
                    <tbody>
                    <tr :class="`table-${colorForTimelineEvent(event.eventType)}`"
                        v-for="event in sortedTimelineEvents">
                        <td>{{event.time}}</td>
                        <td>{{event.eventType}}</td>
                        <td>
                            <a href="#" @click.prevent="viewToken(event.uuid)">
                                {{tokenLookupTable[event.uuid].codeName}}
                            </a>
                        </td>
                    </tr>
                    </tbody>
                </table>
            </div>
        </div>
        <div class="col-8">
            <div class="card">
                <div class="card-header d-flex justify-content-between">
                    <p class="my-1">DAG</p>
                    <div class="btn-group" role="group" aria-label="zoom controls">
                        <button type="button" class="btn btn-secondary" @click="zoomOut">-</button>
                        <button type="button" class="btn btn-secondary" @click="resetZoom">reset</button>
                        <button type="button" class="btn btn-secondary" @click="zoomIn">+</button>
                    </div>
                </div>
                <div class="card-body px-0 py-0">
                    <svg class="border border-info"
                         style="display: inline;
                            width: 100%;
                            height: inherit; "
                         :width="svgWidth"
                         :height="svgHeight"
                         id="dagLayout">
                        <defs>
                            <marker id="arrow" markerWidth="10" markerHeight="10" refX="0" refY="3" orient="auto"
                                    markerUnits="strokeWidth">
                                <path d="M0,0 L0,6 L9,3 z" fill="#2c3e50"/>
                            </marker>
                        </defs>
                        <g class="layers">
                            <g v-for="(layer, layerId) in model.dag"
                               :key="layerId">
                                <g v-for="(token) in layer.tokens"
                                   style="cursor: pointer"
                                   @click="viewToken(token.uuid)"
                                   :key="token.uuid">
                                    <g :transform="`translate(${getTokenX(token.uuid)},${getTokenY(token.uuid)})`">
                                        <rect :width="tokenWidth" :height="tokenHeight"
                                              class="tokenRect"
                                              style="fill: #ecf0f1;"
                                        >
                                        </rect>
                                        <foreignObject :x="tokenTextPaddingX" :y="tokenTextPaddingY"
                                                       :width="tokenWidth - 2 * tokenTextPaddingX"
                                                       :height="tokenHeight - 2 * tokenTextPaddingY">
                                            <div class="card"
                                                 :class="[`border-${colorForTokenDependencyStatus(token.uuid)}`]"
                                                 style="width: 100%; height: 100%; position: static;"
                                                 xmlns="http://www.w3.org/1999/xhtml">
                                                <div class="card-header text-truncate m-0 p-1" :title="token.codeName">
                                                    <template v-if="token === activeToken">
                                                        <span v-if="layerId === 0"
                                                              class="badge badge-warning">Root</span>
                                                        <span v-else-if="token.prerequisites.length === 0"
                                                              class="badge badge-success">Leaf</span>
                                                    </template>
                                                    {{token.codeName}}
                                                </div>
                                                <div class="card-body m-0 p-1">
                                                    <h6 class="card-subtitle text-muted">{{token.returnType}}</h6>
                                                </div>
                                                <div class="card-footer py-1 px-1">
                                                    <span class="badge"
                                                          :class="`badge-${colorForTimelineEvent(tokenLookupTable[token.uuid].executionStatus)}`">
                                                        &nbsp;&nbsp;
                                                    </span>
                                                    <span>
                                                        {{tokenExecutionDuration(token.uuid)}}
                                                    </span>
                                                </div>
                                            </div>
                                        </foreignObject>
                                    </g>
                                    <path v-for="predecessor in token.prerequisites"
                                          :key="predecessor"
                                          :d="lineBetweenToken(token.uuid, predecessor)"
                                          :stroke="activeToken && activeToken.uuid === predecessor ? '#f1c40f' : (activeToken === token ? '#e74c3c' : '#bdc3c7')"
                                          stroke-width="3"
                                          marker-end="url(#arrow)"
                                          fill="none"></path>
                                </g>
                            </g>
                        </g>
                    </svg>
                </div>
            </div>
        </div>
    </div>
</template>

<script>
  Vue.component('logic-dag', {
    props: ['model', 'live'],
    data: function () {
      return {
        tokenWidth: 300,
        tokenHeight: 120,
        tokenPaddingX: 100,
        tokenPaddingY: 40,
        tokenTextPaddingX: 1,
        tokenTextPaddingY: 1,
        svgWidth: 900,
        activeToken: undefined,
        svg: undefined
      }
    },
    computed: {
      tokenLookupTable: function () {
        let lookup = {}
        let index = 0
        this.model.dag.forEach((layer) => {
          layer.tokens.forEach((token) => {
            token.index = index
            index++
            token.successors = []
            lookup[token.uuid] = token
          })
        })
        this.model.timelineEvents.forEach((timelineEvent) => {
          let token = lookup[timelineEvent.uuid]
          token.executionStatus = timelineEvent.eventType
          switch (timelineEvent.eventType) {
            case 'cached':
            case 'blocked':
              break
            case 'started':
              token.startTime = timelineEvent.time
              break
            case 'ended':
            case 'cancelled':
            case 'failed':
              token.completeTime = timelineEvent.time
              break
          }
        })
        this.model.dag.forEach((layer) => {
          layer.tokens.forEach((token) => {
            token.prerequisites.forEach((pre) => {
              if (lookup.hasOwnProperty(pre) && !lookup[pre].successors.includes(token.uuid)) {
                lookup[pre].successors.push(token.uuid)
              }
            })
          })
        })
        return lookup
      },
      sortedTimelineEvents: function () {
        return this.model.timelineEvents.slice().reverse()
      },
      tokensWrappingCount: function () {
        return (this.svgWidth - this.tokenWidth) / this.tokenPaddingX + 1
      },
      svgHeight: function () {
        return Object.keys(this.tokenLookupTable).length * (this.tokenHeight + this.tokenPaddingY) - this.tokenPaddingY
      }
    },
    methods: {
      getTokenX: function (tokenUuid) {
        return this.tokenPaddingX * (this.tokenLookupTable[tokenUuid].index % this.tokensWrappingCount)
      },
      getTokenY: function (tokenUuid) {
        return (this.tokenPaddingY + this.tokenHeight) * this.tokenLookupTable[tokenUuid].index
      },
      lineBetweenToken: function (tokenUuid1, tokenUuid2) {
        let point1 = [this.getTokenX(tokenUuid1) - 30, this.getTokenY(tokenUuid1) + this.tokenHeight * 2 / 3]
        let point2 = [this.getTokenX(tokenUuid2) - 5, this.getTokenY(tokenUuid2) + this.tokenHeight / 3]
        return `M${point2[0]},${point2[1]} C${point2[0] - 150},${point2[1]} ${-100},${point1[1]} ${point1[0]},${point1[1]}`
      },
      tokenExecutionDuration: function (tokenUuid) {
        let token = this.tokenLookupTable[tokenUuid]
        switch (token.executionStatus) {
          case 'failed':
            return this.model.tokenWithException === token.uuid ? 'exception' : 'missing implementation'
          case 'ended':
            return `${token.completeTime - token.startTime} ms`
          case 'running':
            return `since ${token.startTime}`
          default:
            return token.executionStatus
        }
      },
      colorForTokenDependencyStatus: function (tokenUuid) {
        if (!this.activeToken) return 'default'
        if (this.activeToken.uuid === tokenUuid) {
          return 'primary'
        } else if (this.activeToken.prerequisites.includes(tokenUuid)) {
          return 'danger'
        } else if (this.activeToken.successors.includes(tokenUuid)) {
          return 'warning'
        }
        return 'default'
      },
      colorForTimelineEvent: function (eventType) {
        switch (eventType) {
          case 'blocked':
            return 'secondary'
          case 'started':
            return 'warning'
          case 'cached':
          case 'ended':
            return 'success'
          case 'cancelled':
            return 'info'
          case 'failed':
            return 'danger'
        }
      },
      timelineEventsForToken: function (tokenUuid) {
        return this.model.timelineEvents.filter(e => e.uuid === tokenUuid)
      },
      prettyPrintTime: function (ts) {
        return new Date(ts).toISOString()
      },
      viewToken: function (tokenUuid) {
        this.activeToken = this.tokenLookupTable[tokenUuid]
      },
      zoomIn: function () {
        if (this.svg) {
          this.svg.zoomIn()
        }
      },
      zoomOut: function () {
        if (this.svg) {
          this.svg.zoomOut()
        }
      },
      resetZoom: function () {
        if (this.svg) {
          this.svg.reset()
        }
      }
    },
    watch: {
      'model.executionId': function (val, oldVal) {
        this.activeToken = undefined
        this.svg.updateBBox()
        this.svg.reset()
      }
    },
    template: template,
    mounted: function () {
      this.svg = svgPanZoom('#dagLayout', {
        controlIconsEnabled: false,
        fit: false,
        minZoom: 0.2
      })
    }
  })
</script>

<style scoped>
</style>