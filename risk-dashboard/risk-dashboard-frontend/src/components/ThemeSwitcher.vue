<template>
  <div class="theme-switcher">
    <div class="mode-row">
      <button class="mode-btn" :class="{ active: mode === 'dark' }" @click="switchTo('dark')">Dark</button>
      <button class="mode-btn" :class="{ active: mode === 'light' }" @click="switchTo('light')">Light</button>
    </div>
    <div class="dots">
      <span
        v-for="a in currentAccents"
        :key="a[0]"
        class="dot"
        :class="{ on: accent === a[0] }"
        :style="{ background: a[1] }"
        :title="a[0]"
        @click="setAccent(a[0])">
      </span>
    </div>
  </div>
</template>

<script>
const PRESETS = {
  dark: {
    '--color-sidebar-bg': '#0C1524',
    '--color-sidebar-text': '#9AACBF', '--color-sidebar-text-active': '#4A90D9',
    '--color-bg-deep': '#0C1524', '--color-bg-base': '#0F1A2C',
    '--color-bg-elevated': '#121E33', '--color-bg-surface': '#162339', '--color-bg-hover': '#1A2A45',
    '--color-success': '#22C55E', '--color-success-bg': 'rgba(34,197,94,0.10)',
    '--color-warning': '#F59E0B', '--color-warning-bg': 'rgba(245,158,11,0.10)',
    '--color-danger': '#F97316', '--color-danger-bg': 'rgba(249,115,22,0.10)',
    '--color-critical': '#DC2626', '--color-critical-bg': 'rgba(220,38,38,0.10)',
    '--color-text-primary': '#D8DFE8', '--color-text-secondary': '#9AACBF',
    '--color-text-muted': '#5D6F85', '--color-text-disabled': '#3D4F65',
    '--color-border': '#1C2B42', '--color-border-light': '#253652',
    '--map-geo-fill': '#162339', '--map-geo-border': '#1C2B42',
    '--map-alert-fill': '#2D1820', '--map-alert-border': '#552830',
    '--map-tooltip-bg': '#121E33', '--map-tooltip-text': '#D8DFE8',
    '--map-overlay': 'rgba(15,26,44,0.75)',
    '--radius-sm': '2px', '--radius-md': '2px', '--radius-lg': '3px'
  },
  light: {
    '--color-sidebar-bg': '#001529',
    '--color-sidebar-text': 'rgba(255,255,255,0.65)', '--color-sidebar-text-active': '#1677FF',
    '--color-bg-deep': '#F5F5F5', '--color-bg-base': '#F5F5F5',
    '--color-bg-elevated': '#FFFFFF', '--color-bg-surface': '#FAFAFA', '--color-bg-hover': '#F0F0F0',
    '--color-success': '#22C55E', '--color-success-bg': 'rgba(34,197,94,0.06)',
    '--color-warning': '#F59E0B', '--color-warning-bg': 'rgba(245,158,11,0.06)',
    '--color-danger': '#F97316', '--color-danger-bg': 'rgba(249,115,22,0.06)',
    '--color-critical': '#DC2626', '--color-critical-bg': 'rgba(220,38,38,0.06)',
    '--color-text-primary': 'rgba(0,0,0,0.88)', '--color-text-secondary': 'rgba(0,0,0,0.65)',
    '--color-text-muted': 'rgba(0,0,0,0.45)', '--color-text-disabled': 'rgba(0,0,0,0.25)',
    '--color-border': '#F0F0F0', '--color-border-light': '#D9D9D9',
    '--map-geo-fill': '#F0F0F0', '--map-geo-border': '#D9D9D9',
    '--map-alert-fill': '#FFF0F0', '--map-alert-border': '#FFCCCC',
    '--map-tooltip-bg': '#FFFFFF', '--map-tooltip-text': 'rgba(0,0,0,0.88)',
    '--map-overlay': 'rgba(245,245,245,0.75)',
    '--radius-sm': '6px', '--radius-md': '6px', '--radius-lg': '8px'
  }
}

const DARK_ACCENTS = {
  blue:  { p: '#4A90D9', ph: '#5DA0E5', pp: '#3A7BC0', pb: 'rgba(74,144,217,0.10)' },
  cyan:  { p: '#3D9EA8', ph: '#4DB5BF', pp: '#2E858E', pb: 'rgba(61,158,168,0.10)' },
  green: { p: '#3C9D6E', ph: '#4DB580', pp: '#2E8457', pb: 'rgba(60,157,110,0.10)' },
  amber: { p: '#C08830', ph: '#D49A3C', pp: '#A07028', pb: 'rgba(192,136,48,0.10)' },
  slate: { p: '#8A98B0', ph: '#9DACC4', pp: '#6E7D96', pb: 'rgba(138,152,176,0.10)' },
  rose:  { p: '#C05050', ph: '#D46060', pp: '#A04040', pb: 'rgba(192,80,80,0.10)' }
}

const LIGHT_ACCENTS = {
  blue:  { p: '#1677FF', ph: '#4096FF', pp: '#0958D9', pb: 'rgba(22,119,255,0.06)' },
  cyan:  { p: '#0891B2', ph: '#06B6D4', pp: '#0E7490', pb: 'rgba(8,145,178,0.06)' },
  green: { p: '#16A34A', ph: '#22C55E', pp: '#15803D', pb: 'rgba(22,163,74,0.06)' },
  amber: { p: '#D97706', ph: '#F59E0B', pp: '#B45309', pb: 'rgba(217,119,6,0.06)' },
  slate: { p: '#475569', ph: '#64748B', pp: '#334155', pb: 'rgba(71,85,105,0.06)' },
  rose:  { p: '#E11D48', ph: '#F43F5E', pp: '#BE123C', pb: 'rgba(225,29,72,0.06)' }
}

function applyVars(obj) {
  const r = document.documentElement
  Object.entries(obj).forEach(([k, v]) => r.style.setProperty(k, v))
}

function applyAccent(a) {
  const r = document.documentElement
  r.style.setProperty('--color-primary', a.p)
  r.style.setProperty('--color-primary-hover', a.ph)
  r.style.setProperty('--color-primary-pressed', a.pp)
  r.style.setProperty('--color-primary-bg', a.pb)
}

export default {
  name: 'ThemeSwitcher',
  data() {
    return {
      mode: 'dark',
      accent: 'blue',
      darkDots: Object.entries(DARK_ACCENTS).map(([k, v]) => [k, v.p]),
      lightDots: Object.entries(LIGHT_ACCENTS).map(([k, v]) => [k, v.p])
    }
  },
  computed: {
    currentAccents() {
      return this.mode === 'dark' ? this.darkDots : this.lightDots
    }
  },
  mounted() {
    const m = localStorage.getItem('rd-mode') || 'dark'
    const a = localStorage.getItem('rd-accent') || 'blue'
    this.mode = m
    this.accent = a
    applyVars(PRESETS[m])
    const map = m === 'dark' ? DARK_ACCENTS : LIGHT_ACCENTS
    const ac = map[a]
    if (ac) applyAccent(ac)
  },
  methods: {
    switchTo(m) {
      this.mode = m
      localStorage.setItem('rd-mode', m)
      applyVars(PRESETS[m])
      document.dispatchEvent(new CustomEvent('theme-changed'))
      const map = m === 'dark' ? DARK_ACCENTS : LIGHT_ACCENTS
      const ac = map[this.accent] || map['blue']
      if (ac) {
        this.accent = Object.keys(map).find(k => map[k] === ac) || 'blue'
        applyAccent(ac)
      }
    },
    setAccent(k) {
      this.accent = k
      localStorage.setItem('rd-accent', k)
      const map = this.mode === 'dark' ? DARK_ACCENTS : LIGHT_ACCENTS
      const a = map[k]
      if (a) applyAccent(a)
    }
  }
}
</script>

<style scoped>
.theme-switcher {
  padding: 6px 10px;
  border-top: 1px solid var(--color-border);
}

.mode-row {
  display: flex;
  gap: 4px;
  margin-bottom: 6px;
}

.mode-btn {
  flex: 1;
  padding: 3px 0;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  background: transparent;
  color: var(--color-sidebar-text);
  font-size: 10px;
  cursor: pointer;
  font-family: inherit;
}

.mode-btn:hover {
  color: #fff;
  border-color: var(--color-sidebar-text);
}

.mode-btn.active {
  color: var(--color-primary);
  border-color: var(--color-primary);
  background: var(--color-primary-bg);
}

.dots {
  display: flex;
  gap: 5px;
  justify-content: center;
}

.dot {
  width: 14px; height: 14px;
  border-radius: 50%;
  cursor: pointer;
  border: 1px solid transparent;
  opacity: 0.6;
  display: inline-block;
}

.dot:hover { opacity: 1; }

.dot.on {
  border-color: var(--color-sidebar-text);
  opacity: 1;
}
</style>
