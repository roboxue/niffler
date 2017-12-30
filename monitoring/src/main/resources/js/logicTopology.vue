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
                <strong>Prerequisites:</strong>
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
                <strong>Unblocks:</strong>
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
                <g v-for="(layer, layerId) in topology"
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
                              :style="{'fill': '#ecf0f1',
                                  'stroke-width': '3',
                                  'stroke': token === activeToken ? '#e74c3c' :'#2c3e50'}"
                        >
                        </rect>
                        <foreignObject :x="tokenTextPaddingX" :y="tokenTextPaddingY"
                                       :width="tokenWidth - 2 * tokenTextPaddingX"
                                       :height="tokenHeight - 2 * tokenTextPaddingY">
                            <div style="width: 100%; height: 100%; word-wrap: break-word; overflow-y: hidden"
                                 xmlns="http://www.w3.org/1999/xhtml">
                                <p class="mb-0">
                                    <span v-if="token.uuid === targetToken.uuid"
                                          class="badge badge-warning">Target</span>
                                    <span>{{token.codeName}}</span>
                                    <span v-if="activeToken === token" class="badge badge-primary">
                                        Selected
                                    </span>
                                    <span v-else-if="activeToken && activeToken.prerequisites.includes(token.uuid)"
                                          class="badge badge-danger">Prerequisite</span>
                                    <span v-else-if="activeToken && activeToken.successors.includes(token.uuid)"
                                          class="badge badge-success">Successor</span>
                                </p>
                                <p>
                                    <code>{{token.returnType}}</code>
                                    <br/>
                                    {{token.name}}
                                </p>
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
    props: ['topology', 'targetToken'],
    data: function () {
      return {
        tokenWidth: 300,
        tokenHeight: 100,
        tokenPaddingX: 40,
        tokenPaddingY: 40,
        tokenTextPaddingX: 5,
        tokenTextPaddingY: 3,
        activeToken: undefined,
        svg: undefined
      }
    },
    methods: {
      getTokenX: function (layerId, tokenIndex) {
        let firstTokenCompensation = (this.maxLayerWidth - this.topology[layerId].tokens.length) / 2
        let firstTokenMarginLeft = firstTokenCompensation * (this.tokenWidth + this.tokenPaddingX)
        return firstTokenMarginLeft + tokenIndex * (this.tokenWidth + this.tokenPaddingX)
      },
      getLayerY: function (layerId) {
        return layerId * (this.tokenHeight + this.tokenPaddingY)
      },
      viewToken: function (tokenUuid) {
        this.activeToken = this.tokenLookupTable[tokenUuid]
      }
    },
    computed: {
      tokenLookupTable: function () {
        let lookup = {}
        this.topology.forEach((layer, layerIndex) => {
          layer.tokens.forEach((token, tokenIndex) => {
            lookup[token.uuid] = token
          })
        })
        return lookup
      },
      maxLayerWidth: function () {
        if (this.topology.length === 0) {
          return 0
        } else {
          return this.topology.map((layer) => layer.tokens.length).reduce((a, b) => Math.max(a, b))
        }
      },
      layerHeight: function () {
        return this.topology.length
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
    watch: {
      'topology': function (val, oldVal) {
        this.activeToken = undefined
      }
    },
    template: template,
    mounted: function () {
      this.svg = svgPanZoom('#topologyLayout', {
        controlIconsEnabled: true,
        fit: false
      })
    },
    updated: function () {
      this.svg.updateBBox()
      this.svg.resetZoom()
      this.svg.center()
    }
  })
</script>

<style scoped>
</style>