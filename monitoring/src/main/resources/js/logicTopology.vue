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
            <g class="layers">
                <g v-for="(layer, layerId) in model.topology"
                   :key="layerId"
                   :transform="`translate(0 ${getLayerY(layerId)})`"
                >
                    <g v-for="(token, tokenIndex) in layer.tokens"
                       :transform="`translate(${getTokenX(layerId, tokenIndex)})`"
                       style="cursor: pointer"
                       @click="viewToken(token.uuid)"
                       :key="token.uuid">
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
        tokenPaddingX: 40,
        tokenPaddingY: 40,
        tokenTextPaddingX: 1,
        tokenTextPaddingY: 1,
        activeToken: undefined,
        svg: undefined
      }
    },
    computed: {
      tokenLookupTable: function () {
        let lookup = {}
        this.model.topology.forEach((layer) => {
          layer.tokens.forEach((token) => {
            token.successors = []
            lookup[token.uuid] = token
          })
        })
        this.model.timeline.forEach((token) => {
          if (lookup.hasOwnProperty(token.uuid)) {
            lookup[token.uuid].startTime = token.startTime
            lookup[token.uuid].completeTime = token.completeTime
          }
        })
        this.model.ongoing.forEach((token) => {
          if (lookup.hasOwnProperty(token.uuid)) {
            lookup[token.uuid].startTime = token.startedSince
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
          } else if (token.hasOwnProperty('startTime')) {
            if (token.hasOwnProperty('completeTime')) {
              token.executionStatus = 'completed'
            } else {
              token.executionStatus = 'running'
            }
          } else {
            token.executionStatus = 'blocked'
          }
        })
        return lookup
      },
      maxLayerWidth: function () {
        if (this.model.topology.length === 0) {
          return 0
        } else {
          return this.model.topology.map((layer) => layer.tokens.length).reduce((a, b) => Math.max(a, b))
        }
      },
      layerHeight: function () {
        return this.model.topology.length
      },
      svgWidth: function () {
        if (this.maxLayerWidth === 0) return 1
        return this.maxLayerWidth * (this.tokenWidth + this.tokenPaddingX) - this.tokenPaddingX
      },
      svgHeight: function () {
        if (this.layerHeight === 0) return 1
        return this.layerHeight * (this.tokenHeight + this.tokenPaddingY) - this.tokenPaddingY
      }
    },
    methods: {
      getTokenX: function (layerId, tokenIndex) {
        let firstTokenCompensation = (this.maxLayerWidth - this.model.topology[layerId].tokens.length) / 2
        let firstTokenMarginLeft = firstTokenCompensation * (this.tokenWidth + this.tokenPaddingX)
        return firstTokenMarginLeft + tokenIndex * (this.tokenWidth + this.tokenPaddingX)
      },
      getLayerY: function (layerId) {
        return layerId * (this.tokenHeight + this.tokenPaddingY)
      },
      tokenExecutionDuration: function (tokenUuid) {
        let token = this.tokenLookupTable[tokenUuid]
        switch (token.executionStatus) {
          case 'failed':
            return this.model.tokenWithException === token.uuid ? "exception" : "missing implementation"
          case 'completed':
            return `${token.completeTime - token.startTime} ms`
          case 'running':
            return `since ${token.startTime}`
          case 'blocked':
            return `unstarted`
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
          case 'blocked':
            return 'secondary'
        }
        return 'default'
      },
      viewToken: function (tokenUuid) {
        this.activeToken = this.tokenLookupTable[tokenUuid]
      }
    },
    watch: {
      'model': function (val, oldVal) {
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