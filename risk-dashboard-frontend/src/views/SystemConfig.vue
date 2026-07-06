<template>
  <div class="full-page">
    <div class="page-header"><h2 class="page-title">系统配置</h2></div>

    <div class="panel flex-1" style="margin-top:6px;">
      <div class="panel-header"><span>风险评分规则 (60条)</span><span style="font-size:10px;color:var(--color-text-muted);">硬阻断:1 | 硬规则:23 | 软规则:36</span></div>
      <el-table :data="rules" size="mini" max-height="100%" class="flex-table" row-class-name="rule-row">
        <el-table-column prop="id" label="#" width="60" />
        <el-table-column prop="category" label="类别" width="80">
          <template slot-scope="{ row }"><el-tag :type="ct(row.category)" size="mini" effect="dark">{{ row.category }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="name" label="规则名称" width="170" />
        <el-table-column prop="condition" label="判定条件" min-width="500" show-overflow-tooltip />
        <el-table-column prop="scoreDisplay" label="分值" width="100">
          <template slot-scope="{ row }"><el-tag :type="st(row)" size="mini" effect="dark">{{ row.scoreDisplay }}</el-tag></template>
        </el-table-column>
      </el-table>
      <div style="padding:6px 0;font-size:14px;color:var(--color-text-muted);flex-shrink:0;display:flex;gap:16px;">
        <span><span class="sd" style="background:var(--color-success)"></span> 0-40 低危</span>
        <span><span class="sd" style="background:var(--color-warning)"></span> 41-70 中危</span>
        <span><span class="sd" style="background:var(--color-danger)"></span> 71-120 高危</span>
        <span><span class="sd" style="background:var(--color-critical)"></span> &gt;120 极度危险</span>
        <span><span class="sd" style="background:#000"></span> 硬阻断</span>
      </div>
    </div>
  </div>
</template>

<script>
export default {
  name: 'SystemConfig',
  data() { return { rules: buildRules() } },
  methods: {
    ct(c) { return { 'A': '', 'B': 'info', 'C': 'warning', 'D': 'danger', 'E': '', 'F': 'danger', 'G': 'info', 'H': 'danger', 'I': 'warning' }[c] || '' },
    st(r) { if (r.block === '硬阻断') return 'danger'; return r.ruleType === '硬规则' ? 'warning' : 'success' }
  }
}
function buildRules() {
  return [
    { id: 'A1', category: 'A', name: '账户盗用', condition: 'login_fail_count ≥ 5 且 ip_address ≠ last_ip 且 amount > 5000', score: 60, ruleType: '硬规则', block: '' },
    { id: 'A2', category: 'A', name: '撞库攻击', condition: '同IP 5分钟内 login_fail_count ≥ 10 个不同 user_id (全局统计)', score: 50, ruleType: '硬规则', block: '' },
    { id: 'A3', category: 'A', name: '新设备登录转账', condition: 'device_id ∉ common_devs 且 amount > 5000', score: 30, ruleType: '软规则', block: '' },
    { id: 'A4', category: 'A', name: '休眠账户唤醒', condition: '(now - last_trans_ts) > 180天 且 amount > avg_amt_30d × 5', score: 40, ruleType: '软规则', block: '' },
    { id: 'A5', category: 'A', name: '新注册大额转账', condition: '(now - registration_time) < 24小时 且 amount > 10000', score: 50, ruleType: '硬规则', block: '' },
    { id: 'A6', category: 'A', name: '账户被标记', condition: "account_status = 'flagged' 或 risk_tags 含 'victim'", score: 35, ruleType: '软规则', block: '' },
    { id: 'A7', category: 'A', name: '多账号同设备', condition: '同一 device_id 关联 ≥ 5 个不同 user_id (全局统计)', score: 45, ruleType: '硬规则', block: '' },
    { id: 'A8', category: 'A', name: '异常快速重登录', condition: '(login_time - last_login_time) < 60秒 且 ip_address ≠ last_ip', score: 25, ruleType: '软规则', block: '' },
    { id: 'B1', category: 'B', name: '设备Root/越狱', condition: 'root_jailbreak = true', score: 50, ruleType: '硬规则', block: '' },
    { id: 'B2', category: 'B', name: '模拟器环境', condition: 'battery_level = 100 且 screen_resolution 非标准手机比例 且 user_agent 无移动特征', score: 40, ruleType: '软规则', block: '' },
    { id: 'B3', category: 'B', name: '设备频繁更换', condition: '同 user_id 30天内 device_id 变化次数 ≥ 5', score: 35, ruleType: '软规则', block: '' },
    { id: 'B4', category: 'B', name: '系统版本过旧', condition: "(os_type='Android' 且 os_version < '8.0') 或 (os_type='iOS' 且 os_version < '12.0')", score: 15, ruleType: '软规则', block: '' },
    { id: 'B5', category: 'B', name: '无SIM卡', condition: 'sim_operator = null 或 sim_operator = \'\'', score: 20, ruleType: '软规则', block: '' },
    { id: 'B6', category: 'B', name: '自动化浏览器', condition: "user_agent 包含 'HeadlessChrome'/'PhantomJS'/'Selenium'", score: 45, ruleType: '硬规则', block: '' },
    { id: 'C1', category: 'C', name: '金额突变', condition: 'amount > avg_amt_30d × 3', score: 30, ruleType: '软规则', block: '' },
    { id: 'C2', category: 'C', name: '大额整数转账', condition: 'amount ≥ 50000 且 amount % 10000 = 0', score: 15, ruleType: '软规则', block: '' },
    { id: 'C3', category: 'C', name: '逼近单笔限额', condition: 'amount ≥ single_limit × 0.95 且 amount < single_limit', score: 20, ruleType: '软规则', block: '' },
    { id: 'C4', category: 'C', name: '余额清零', condition: 'amount ≥ total_balance × 0.98', score: 40, ruleType: '硬规则', block: '' },
    { id: 'C5', category: 'C', name: '小额测试后大额', condition: 'amount < 1 且该用户此前有 amount < 1 的测试交易记录', score: 10, ruleType: '软规则', block: '' },
    { id: 'C6', category: 'C', name: '大额拆分', condition: 'trans_count_24h ≥ 3 且每笔 amount 在 45000-49999 之间', score: 45, ruleType: '硬规则', block: '' },
    { id: 'C7', category: 'C', name: '日累计超限', condition: '(trans_amount_24h + amount) > daily_limit', score: 25, ruleType: '软规则', block: '' },
    { id: 'C8', category: 'C', name: '月累计超限', condition: '(trans_amount_7d × 4.3 + amount) > monthly_limit', score: 20, ruleType: '软规则', block: '' },
    { id: 'D1', category: 'D', name: '地理偏离', condition: 'city ∉ common_cities 列表', score: 20, ruleType: '软规则', block: '' },
    { id: 'D2', category: 'D', name: '异地瞬移', condition: '(GPS距离 ÷ 时间间隔) > 1000km/h', score: 70, ruleType: '硬规则', block: '' },
    { id: 'D3', category: 'D', name: 'IP与GPS不一致', condition: 'IP归属城市 ≠ GPS坐标城市 且 直线距离 > 200km', score: 40, ruleType: '软规则', block: '' },
    { id: 'D4', category: 'D', name: '跨境异常', condition: 'IP归属国家 ≠ 账户注册国家', score: 45, ruleType: '硬规则', block: '' },
    { id: 'D5', category: 'D', name: '电诈高危地区', condition: 'city 或 ip_address 在电诈高发地区名单中', score: 60, ruleType: '硬规则', block: '' },
    { id: 'D6', category: 'D', name: '基站位置异常', condition: 'sim_operator 归属城市 ≠ GPS坐标城市', score: 30, ruleType: '软规则', block: '' },
    { id: 'E1', category: 'E', name: '深夜大额交易', condition: '交易时间在 02:00-05:00 且 amount > 5000', score: 20, ruleType: '软规则', block: '' },
    { id: 'E2', category: 'E', name: '非营业日对公转账', condition: "周末/法定节假日 且 trans_type = '对公转账'", score: 25, ruleType: '软规则', block: '' },
    { id: 'E3', category: 'E', name: '登录后极速转账', condition: '(交易时间 - 登录时间) < 10秒 且 amount > 10000', score: 35, ruleType: '硬规则', block: '' },
    { id: 'E4', category: 'E', name: '高频密集交易', condition: '同 user_id 60秒内 trans_count ≥ 3', score: 40, ruleType: '硬规则', block: '' },
    { id: 'E5', category: 'E', name: '页面停留过短', condition: 'click_duration < 800ms 且 amount > 5000', score: 30, ruleType: '软规则', block: '' },
    { id: 'F1', category: 'F', name: '收款方高危黑名单', condition: "counterparty_id 在 counterparty_blacklist 且 risk_level = 'high'", score: '--', ruleType: '硬规则', block: '硬阻断' },
    { id: 'F2', category: 'F', name: '收款方中风险', condition: "counterparty_id 在 counterparty_blacklist 且 risk_level = 'medium'", score: 50, ruleType: '硬规则', block: '' },
    { id: 'F3', category: 'F', name: '首次转账给该收款方', condition: 'counterparty_id ∉ common_counterparties', score: 20, ruleType: '软规则', block: '' },
    { id: 'F4', category: 'F', name: '新注册收款方', condition: '收款方 (now - registration_time) < 7天', score: 35, ruleType: '软规则', block: '' },
    { id: 'F5', category: 'F', name: '收款方快进快出', condition: '收款方 total_received_24h / total_received_7d > 0.8 且 unique_payers_24h ≥ 10', score: 55, ruleType: '硬规则', block: '' },
    { id: 'F6', category: 'F', name: '个人转对公可疑', condition: "trans_type = '个人' 且 counterparty_bank 含 '对公'/'企业'", score: 30, ruleType: '软规则', block: '' },
    { id: 'F7', category: 'F', name: '多对一集中收款', condition: '收款方 unique_payers_24h ≥ 20 且 total_received_24h > 500000', score: 60, ruleType: '硬规则', block: '' },
    { id: 'F8', category: 'F', name: '收款方被标记', condition: "counterparty_id 的 risk_tags 包含 'fraud'/'money_mule'", score: 40, ruleType: '软规则', block: '' },
    { id: 'G1', category: 'G', name: '密码粘贴输入', condition: "input_method = 'paste' 且 amount > 10000", score: 25, ruleType: '软规则', block: '' },
    { id: 'G2', category: 'G', name: '越狱设备自动填充', condition: "input_method = 'autofill' 且 os_type = 'Android' 且 root_jailbreak = true", score: 30, ruleType: '软规则', block: '' },
    { id: 'G3', category: 'G', name: '反复修改收款方', condition: '同一 trans_id 提交前修改 counterparty_id ≥ 3次', score: 15, ruleType: '软规则', block: '' },
    { id: 'G4', category: 'G', name: '取消后立即重试', condition: 'cancel_retry_count ≥ 2 且本次交易金额/收款方与上次取消的相同', score: 35, ruleType: '软规则', block: '' },
    { id: 'G5', category: 'G', name: '切换支付渠道', condition: 'pay_channel ∉ common_pay_channels', score: 20, ruleType: '软规则', block: '' },
    { id: 'G6', category: 'G', name: '备注含诈骗敏感词', condition: 'note 匹配关键词：投资/返利/导师/稳赚/内幕/VIP群/刷单', score: 40, ruleType: '软规则', block: '' },
    { id: 'G7', category: 'G', name: '来源页面异常', condition: "page_url 以 'http://' 开头 (非HTTPS) 或域名不在白名单", score: 25, ruleType: '软规则', block: '' },
    { id: 'H1', category: 'H', name: '资金回流', condition: '30分钟内存在链 A→B→A (user_id 与 to_user_id 互换)', score: 70, ruleType: '硬规则', block: '' },
    { id: 'H2', category: 'H', name: '多级跳转', condition: 'trans_chain 中 hop_order ≥ 3 且 chain_depth ≥ 3', score: 50, ruleType: '硬规则', block: '' },
    { id: 'H3', category: 'H', name: '汇聚分散', condition: '用户收到 ≥ 5笔不同对方小额汇款后，一次性大额转出', score: 55, ruleType: '硬规则', block: '' },
    { id: 'H4', category: 'H', name: '分散汇聚', condition: '用户收到1笔大额后，拆分成 ≥ 5笔小额转给不同对方', score: 50, ruleType: '硬规则', block: '' },
    { id: 'H5', category: 'H', name: '三人以上环形链路', condition: 'trans_chain.is_loop = 1 且参与方 ≥ 3', score: 65, ruleType: '硬规则', block: '' },
    { id: 'H6', category: 'H', name: '僵尸账户中转', condition: "链路中出现 account_status = 'frozen'/'dormant' 的中间节点", score: 40, ruleType: '软规则', block: '' },
    { id: 'I1', category: 'I', name: 'VPN/代理', condition: "network_type = 'VPN' 或 ip_address 在已知代理/VPN IP库中", score: 35, ruleType: '软规则', block: '' },
    { id: 'I2', category: 'I', name: 'Tor网络', condition: 'ip_address 属于 Tor出口节点列表', score: 50, ruleType: '硬规则', block: '' },
    { id: 'I3', category: 'I', name: '公共WiFi大额转账', condition: 'wifi_ssid 匹配公共热点列表 且 amount > 10000', score: 15, ruleType: '软规则', block: '' },
    { id: 'I4', category: 'I', name: 'DNS异常', condition: 'dns_server 不在国内主流DNS列表', score: 20, ruleType: '软规则', block: '' },
    { id: 'I5', category: 'I', name: '低安全分设备', condition: 'dev_score < 50', score: 30, ruleType: '软规则', block: '' },
    { id: 'I6', category: 'I', name: 'HTTP明文页面', condition: "page_url 以 'http://' 开头 (非加密传输)", score: 10, ruleType: '软规则', block: '' },
  ].map(r => ({ ...r, scoreDisplay: r.block === '硬阻断' ? '直接拦截' : `+${r.score}` }))
}
</script>

<style scoped>
.full-page { height: 100%; display: flex; flex-direction: column; overflow: hidden; }
.page-header { padding-bottom: 6px; border-bottom: 1px solid var(--color-border); flex-shrink: 0; }
.page-title { color: var(--color-text-primary); font-size: 15px; font-weight: 600; margin: 0; }

.flex-1 { flex: 1; min-height: 0; height: 0; }
.panel { background: var(--color-bg-elevated); border: 1px solid var(--color-border); border-radius: var(--radius-sm); padding: 6px 8px; display: flex; flex-direction: column; overflow: hidden; }
.panel-header { display: flex; justify-content: space-between; align-items: center; padding-bottom: 4px; border-bottom: 1px solid var(--color-border); flex-shrink: 0; font-size: 11px; color: var(--color-text-secondary); font-weight: 500; }
.flex-table { flex: 1; min-height: 0; }

.sd { display: inline-block; width: 10px; height: 8px; border-radius: 1px; flex-shrink: 0; vertical-align: middle; margin-right: 3px; }
</style>
