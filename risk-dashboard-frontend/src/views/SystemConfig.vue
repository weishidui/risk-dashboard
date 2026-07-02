<template>
  <div class="system-config">
    <div class="page-header">
      <h2 class="page-title">系统配置</h2>
    </div>

    <div class="panel">
      <div class="panel-header">
        <span class="panel-title">数据接入接口说明</span>
      </div>
      <el-collapse>
        <el-collapse-item title="POST /api/data/transaction — 交易流水数据接入" name="1">
          <p>Kafka → Spark Streaming 实时流处理后调用。需求文档 3.1 节。</p>
          <pre>{
  "transId": "TXN20260630120000001",
  "userId": "USER10086",
  "amount": 15000.00,
  "timestamp": 1719734400000,
  "city": "北京",
  "geoLocation": "116.3,39.9",
  "deviceId": "DEVICE_A8F3",
  "networkType": "4G",
  "devScore": 85
}</pre>
        </el-collapse-item>

        <el-collapse-item title="POST /api/data/alert — 风控告警结果接入" name="2">
          <p>Spark Streaming 实时风控引擎判定后调用。需求文档 3.3 节。</p>
          <pre>{
  "alertId": "ALT20260630120000001",
  "transId": "TXN20260630120000001",
  "userId": "USER10086",
  "amount": 15000.00,
  "city": "深圳",
  "hitRules": "金额突变;异地瞬移",
  "finalScore": 85,
  "riskLevel": "高危",
  "alertLoc": "深圳",
  "rawJson": "{\"trans_id\":\"TXN20260630120000001\",\"amount\":15000}"
}</pre>
        </el-collapse-item>

        <el-collapse-item title="POST /api/data/profile — 用户画像数据接入" name="3">
          <p>HDFS → Spark SQL 离线批处理计算后调用。需求文档 3.2 节。</p>
          <pre>{
  "userId": "USER10086",
  "avgAmt30d": 5000.00,
  "commonCities": "北京,上海,广州",
  "commonDevs": "D9001,D9002",
  "lastTransTs": 1719734000000,
  "lastCity": "北京"
}</pre>
        </el-collapse-item>

        <el-collapse-item title="POST /api/data/metrics — 实时指标快照接入" name="4">
          <p>Spark Streaming 窗口聚合计算后调用。</p>
          <pre>{
  "snapshotTime": 1719734400000,
  "totalTransactions": 1200,
  "passCount": 800, "verifyCount": 300, "blockCount": 100,
  "highRiskCount": 100, "mediumRiskCount": 300, "lowRiskCount": 800,
  "avgRiskScore": 45.5, "avgLatency": 350
}</pre>
        </el-collapse-item>
      </el-collapse>
    </div>

    <div class="panel" style="margin-top:10px;">
      <div class="panel-header">
        <span class="panel-title">风险评分规则配置</span>
      </div>
      <el-table :data="ruleConfigs" size="mini">
        <el-table-column prop="name" label="规则" width="100" />
        <el-table-column prop="condition" label="判定条件" min-width="260" />
        <el-table-column prop="score" label="分值" width="70">
          <template slot-scope="{ row }">
            <el-tag type="danger" size="mini" effect="dark">+{{ row.score }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="description" label="说明" min-width="170" />
      </el-table>

      <el-divider />

      <div class="score-summary">
        <div class="summary-item"><span class="summary-dot success"></span> &lt;60 → 低危 / 放行</div>
        <div class="summary-item"><span class="summary-dot warning"></span> 60-80 → 中危 / 核验</div>
        <div class="summary-item"><span class="summary-dot danger"></span> &gt;80 → 高危 / 拦截</div>
      </div>
    </div>
  </div>
</template>

<script>
export default {
  name: 'SystemConfig',
  data() {
    return {
      ruleConfigs: [
        { name: '金额异常', condition: 'amount > avgAmt30d x 3', score: 30, description: '当前消费超历史均值3倍' },
        { name: '地理偏离', condition: 'city 不在 commonCities 列表中', score: 20, description: '交易城市非常用城市' },
        { name: '异地瞬移', condition: '(当前坐标 - 上次坐标) / 时间间隔 > 1000km/h', score: 80, description: '位移速度超过物理极限' },
        { name: '环境风险', condition: 'networkType = "VPN" 或 devScore < 50', score: 40, description: '网络环境异常或设备风险' }
      ]
    }
  }
}
</script>

<style scoped>
.system-config { min-height: 100%; }

.page-header {
  margin-bottom: var(--space-3);
  padding-bottom: var(--space-3);
  border-bottom: 1px solid var(--color-border);
}

.page-title {
  color: var(--color-text-primary);
  font-size: var(--text-lg);
  font-weight: 600;
  margin: 0;
}

.panel {
  background: var(--color-bg-elevated);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  padding: var(--space-3);
}

.panel-header {
  margin-bottom: var(--space-2);
  padding-bottom: var(--space-2);
  border-bottom: 1px solid var(--color-border);
}

.panel-title {
  color: var(--color-text-secondary);
  font-size: var(--text-sm);
  font-weight: 600;
}

p {
  color: var(--color-text-secondary);
  font-size: var(--text-sm);
  margin-bottom: var(--space-2);
}

pre {
  background: var(--color-bg-deep);
  color: #7BA68C;
  padding: var(--space-3);
  border-radius: var(--radius-sm);
  font-family: var(--font-mono);
  font-size: 11px;
  overflow-x: auto;
  max-height: 260px;
  border: 1px solid var(--color-border);
  line-height: 1.5;
}

.score-summary {
  font-size: var(--text-sm);
  color: var(--color-text-secondary);
  line-height: 2;
}

.summary-item {
  display: flex;
  align-items: center;
  gap: var(--space-2);
}

.summary-dot {
  width: 8px; height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
}

.summary-dot.success { background: var(--color-success); }
.summary-dot.warning { background: var(--color-warning); }
.summary-dot.danger  { background: var(--color-danger); }
</style>
