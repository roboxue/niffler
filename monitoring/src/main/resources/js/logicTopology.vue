<template>
    <div style="width: 100%; height: 100%; max-height: 900px">
        <div class="card">
            <div class="card-header">Token View</div>
            <div class="card-body" v-if="activeToken !== undefined">
                <div class="d-flex justify-content-between">
                    <h3 class="card-title">{{activeToken.codeName}}</h3>
                    <code>{{activeToken.returnType}}</code>
                </div>
                <p class="card-text">{{activeToken.name}}</p>
                <span class="badge badge-danger">Prerequisites:</span>
                <div v-if="activeToken.prerequisites.length > 0">
                    <a class="card-link"
                       href="#"
                       v-for="uuid in activeToken.prerequisites"
                       @click.prevent="viewToken(uuid)"
                       :key="uuid">
                        {{tokenLookupTable[uuid].codeName}}
                    </a>
                </div>
                <p v-else>(none)</p>
                <span class="badge badge-warning">Unblocks:</span>
                <div v-if="activeToken.successors.length > 0">
                    <a class="card-link"
                       href="#"
                       v-for="uuid in activeToken.successors"
                       @click.prevent="viewToken(uuid)"
                       :key="uuid">
                        {{tokenLookupTable[uuid].codeName}}
                    </a>
                </div>
                <p v-else>(none)</p>
                <span v-if="activeToken.hasOwnProperty('startTime')">
                    <strong>Started:</strong> <span>{{activeToken.startTime}}</span>
                    <strong>Completed:</strong> <span>{{activeToken.completeTime || 'Not completed yet'}}</span>
                </span>
                <span v-else>
                    Not started yet
                </span>
            </div>
            <div class="card-body" v-else>
                <p class="card-text">Select a token to view details</p>
            </div>
        </div>
        <svg class="border border-info"
             style="display: inline;
                    width: inherit; min-width: inherit; max-width: inherit;
                    height: inherit; min-height: inherit; max-height: inherit; "
             :width="svgWidth"
             :height="svgHeight"
             id="topologyLayout">
            <defs>
                <marker id="arrow" markerWidth="10" markerHeight="10" refX="0" refY="3" orient="auto" markerUnits="strokeWidth">
                    <path d="M0,0 L0,6 L9,3 z" fill="#2c3e50" />
                </marker>
            </defs>
            <g class="layers">
                <g v-for="(layer, layerId) in model.topology"
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
                                    <div class="card-header text-truncate m-0 p-1">
                                        <template v-if="token === activeToken">
                                            <span v-if="layerId === 0" class="badge badge-warning">Root</span>
                                            <span v-else-if="token.prerequisites.length === 0" class="badge badge-success">Leaf</span>
                                        </template>
                                        {{token.codeName}}
                                    </div>
                                    <div class="card-body m-0 p-1">
                                        <h6 class="card-subtitle text-muted">{{token.returnType}}</h6>
                                        <p class="card-text text-truncate d-inline-block" style="width: 100%;"
                                           :class="[`text-${colorForTokenDependencyStatus(token.uuid)}`]">
                                            {{token.name}}</p>
                                    </div>
                                    <div class="card-footer py-1 px-1">
                                    <span class="badge" :class="`badge-${colorForTokenExecutionStatus(token.uuid)}`">
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
</template>

<script>
  Vue.component('logic-topology', {
    props: ['model'],
    data: function () {
      return {
        tokenWidth: 350,
        tokenHeight: 150,
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
        this.model.topology.forEach((layer) => {
          layer.tokens.forEach((token) => {
            token.index = index
            index++
            token.successors = []
            lookup[token.uuid] = token
          })
        })
        this.model.timeline.forEach((token) => {
          if (lookup.hasOwnProperty(token.uuid)) {
            lookup[token.uuid].status = token.status
            lookup[token.uuid].startTime = token.startTime
            lookup[token.uuid].completeTime = token.completeTime
          }
        })
        this.model.topology.forEach((layer) => {
          layer.tokens.forEach((token) => {
            token.prerequisites.forEach((pre) => {
              if (lookup.hasOwnProperty(pre) && !lookup[pre].successors.includes(token.uuid)) {
                lookup[pre].successors.push(token.uuid)
              }
            })
          })
        })
        Object.values(lookup).forEach(token => {
          if (this.model.tokenWithException === token.uuid || (this.model.tokensMissingImpl || []).includes(token.uuid)) {
            token.executionStatus = 'failed'
          } else if (token.hasOwnProperty('status')) {
            token.executionStatus = token.status
          } else {
            token.executionStatus = 'blocked'
          }
        })
        return lookup
      },
      tokensWrappingCount: function () {
        return (this.svgWidth - this.tokenWidth) / this.tokenPaddingX + 1
      },
      svgHeight: function () {
        return this.tokenLookupTable.length * (this.tokenHeight + this.tokenPaddingY) - this.tokenPaddingY
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
        return `M${point2[0]},${point2[1]} C${point2[0] - 150},${point2[1]} ${- 100},${point1[1]} ${point1[0]},${point1[1]}`
      },
      tokenExecutionDuration: function (tokenUuid) {
        let token = this.tokenLookupTable[tokenUuid]
        switch (token.executionStatus) {
          case 'failed':
            return this.model.tokenWithException === token.uuid ? 'exception' : 'missing implementation'
          case 'completed':
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
      colorForTokenExecutionStatus: function (tokenUuid) {
        let token = this.tokenLookupTable[tokenUuid]
        switch (token.executionStatus) {
          case 'failed':
            return 'danger'
          case 'completed':
            return 'success'
          case 'running':
            return 'warning'
          case 'cached':
          case 'injected':
          case 'blocked':
            return 'secondary'
        }
        console.log(token.executionStatus)
        return 'default'
      },
      viewToken: function (tokenUuid) {
        this.activeToken = this.tokenLookupTable[tokenUuid]
      }
    },
    watch: {
      'model.executionId': function (val, oldVal) {
        this.activeToken = undefined
        this.svg.updateBBox()
      }
    },
    template: template,
    mounted: function () {
      this.svg = svgPanZoom('#topologyLayout', {
        controlIconsEnabled: true,
        fit: false,
        minZoom: 0.2
      })
    }
  })
</script>

<style scoped>
</style>