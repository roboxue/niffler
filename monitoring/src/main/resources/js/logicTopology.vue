<template>
    <div class="border border-info" style="width: 100%; height: 100%; max-height: 900px">
        <svg style="display: inline;
                    width: inherit; min-width: inherit; max-width: inherit;
                    height: inherit; min-height: inherit; max-height: inherit; "
             :width="svgWidth"
             :height="svgHeight"
             id="topologyLayout">
            <g class="layers">
                <g v-for="(layer, layerId) in model"
                   :key="layerId"
                   :transform="`translate(0 ${getLayerY(layerId)})`"
                >
                    <g v-for="(token, tokenIndex) in layer.tokens"
                       :transform="`translate(${getTokenX(layerId, tokenIndex)})`"
                       @click="viewToken(token.uuid)"
                       :key="token.uuid">
                        <rect :width="tokenWidth" :height="tokenHeight"
                              style="fill: #ecf0f1;
                                  stroke-width: 3;
                                  stroke: #2c3e50"
                        >
                        </rect>
                        <foreignObject :x="tokenTextPaddingX" :y="tokenTextPaddingY"
                                       :width="tokenWidth - 2 * tokenTextPaddingX"
                                       :height="tokenHeight - 2 * tokenTextPaddingY">
                            <div style="width: 100%; height: 100%; word-wrap: break-word; overflow-y: hidden"
                                 xmlns="http://www.w3.org/1999/xhtml">
                                <p>
                                    <strong>{{token.codeName}}</strong>
                                    <br/>
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
    props: ['model'],
    data: function () {
      return {
        tokenWidth: 300,
        tokenHeight: 100,
        tokenPaddingX: 40,
        tokenPaddingY: 40,
        tokenTextPaddingX: 5,
        tokenTextPaddingY: 3,
        svg: undefined
      }
    },
    methods: {
      getTokenX: function (layerId, tokenIndex) {
        let firstTokenCompensation = (this.maxLayerWidth - this.model[layerId].tokens.length) / 2
        let firstTokenMarginLeft = firstTokenCompensation * (this.tokenWidth + this.tokenPaddingX)
        return firstTokenMarginLeft + tokenIndex * (this.tokenWidth + this.tokenPaddingX)
      },
      getLayerY: function (layerId) {
        return layerId * (this.tokenHeight + this.tokenPaddingY)
      },
      viewToken: function (tokenUuid) {
        this.$emit("viewToken", tokenUuid)
      }
    },
    computed: {
      maxLayerWidth: function () {
        if (this.model.length === 0) {
          return 0
        } else {
          return this.model.map((layer) => layer.tokens.length).reduce((a, b) => Math.max(a, b))
        }
      },
      layerHeight: function () {
        return this.model.length
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