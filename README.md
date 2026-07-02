# 金融交易风险实时监控与分析平台 - 可视化展示端

## 📋 项目概述

基于 **Lambda 架构** 的金融风控系统可视化展示端，由 **Spring Boot (Java 1.8)** 后端和 **Vue 2.x + ECharts** 前端组成。

项目严格遵循需求文档 [需求分析文档.md](./需求分析文档.md) 的数据字典规范（第3章）和业务场景逻辑（第4章）。

### 架构全景

```
┌─────────────────────────────────────────────────────────────┐
│                    可视化展示端 (本项目)                       │
│  ┌──────────────────────┐    ┌───────────────────────────┐  │
│  │   Vue 2.x 前端大屏    │◄──►│  Spring Boot 后端服务      │  │
│  │   (ECharts + Element) │    │  (REST API + WebSocket)   │  │
│  └──────────────────────┘    └──────┬────────┬───────────┘  │
│                                     │        │               │
│                              MySQL  │  Redis │               │
└─────────────────────────────────────┼────────┼───────────────┘
                                      │        │
        ┌─────────────────────────────┘        └───────────────┐
        │                                                       │
   ┌────▼──────────┐                          ┌────────────────▼┐
   │ Spark Streaming│                          │  Spark SQL/MR    │
   │ (实时风控引擎)  │                          │  (离线画像计算)   │
   └───────┬────────┘                          └────────┬─────────┘
           │                                            │
      ┌────▼───┐                                  ┌─────▼──────┐
      │  Kafka  │                                  │    HDFS    │
      └─────────┘                                  └────────────┘
```

---

## 🚀 快速启动

### 开发版本 (无需 MySQL/Redis)

```bash
# 1. 启动后端 (H2内存数据库)
cd risk-dashboard-backend
mvn clean package -DskipTests
java -jar target/risk-dashboard-backend-1.0.0.jar

# 后端启动后访问:
#   API文档: http://localhost:8080/swagger-ui/index.html
#   H2控制台: http://localhost:8080/h2-console

# 2. 启动前端
cd risk-dashboard-frontend
npm install
npm run serve

# 前端访问: http://localhost:8081
```

### 生产版本 (MySQL + Redis)

```bash
# 1. 初始化数据库
mysql -u root -p < risk-dashboard-backend/src/main/resources/db/schema.sql

# 2. 配置环境变量
export MYSQL_PASSWORD=your_password
export REDIS_HOST=192.168.1.100
export REDIS_PASSWORD=your_redis_password

# 3. 启动
java -jar risk-dashboard-backend-1.0.0.jar --spring.profiles.active=prod
```

---

## 📂 项目结构

### 后端 (risk-dashboard-backend/)

```
src/main/java/com/finance/risk/dashboard/
├── RiskDashboardApplication.java      # 启动类
├── config/
│   ├── CorsConfig.java               # 跨域配置
│   ├── SwaggerConfig.java            # API文档配置
│   ├── WebSocketConfig.java          # WebSocket配置
│   └── RedisConfig.java              # Redis序列化配置
├── controller/
│   ├── DataReceiveController.java     # ★ 数据接入接口 (供上游数据处理调用)
│   ├── DashboardController.java      # 仪表盘接口 (供前端大屏调用)
│   ├── AlertController.java          # 告警管理接口
│   ├── TransactionController.java    # 交易流水接口
│   └── MetricsController.java        # 指标统计接口
├── service/
│   ├── AlertService.java / impl/     # 告警服务
│   ├── TransactionService.java       # 交易服务
│   ├── MetricsService.java           # 指标聚合服务
│   └── ProfileService.java           # 用户画像服务
├── dao/
│   ├── AlertDao.java                 # 告警数据访问 (MyBatis)
│   ├── TransactionDao.java           # 交易数据访问
│   └── MetricsDao.java               # 指标数据访问
├── entity/                           # 实体类 (对应数据字典)
│   ├── Transaction.java              #   3.1 交易流水
│   ├── AlertResult.java              #   3.3 告警结果
│   ├── UserProfile.java              #   3.2 用户画像
│   └── MetricsSnapshot.java          #   指标快照
├── dto/                              # ★ 数据接入DTO (上游数据处理程序使用)
│   ├── TransactionInputDTO.java      #   交易流水接入
│   ├── AlertInputDTO.java            #   告警结果接入
│   ├── ProfileInputDTO.java          #   用户画像接入
│   └── MetricsInputDTO.java          #   指标快照接入
├── vo/                               # 视图对象 (前端展示)
├── common/
│   ├── RiskLevelEnum.java            # 风险等级枚举
│   ├── RuleTypeEnum.java             # 规则类型枚举
│   └── Constants.java                # 系统常量
└── websocket/
    └── RiskWebSocketHandler.java      # WebSocket实时推送
```

### 前端 (risk-dashboard-frontend/)

```
src/
├── main.js                           # 入口
├── App.vue                           # 主布局 (侧边栏+内容区)
├── router/index.js                   # 路由配置
├── store/index.js                    # Vuex状态管理
├── api/
│   ├── request.js                    # Axios封装 (统一拦截器)
│   ├── metrics.js / alert.js / transaction.js  # API模块
├── views/
│   ├── Dashboard.vue                 # ★ 实时监控仪表盘 (核心大屏)
│   ├── TransactionFlow.vue           # 交易流水监控
│   ├── AlertManage.vue               # 风险告警管理
│   ├── RiskMap.vue                   # 风险地理分布
│   ├── DataAnalysis.vue              # 数据统计分析
│   └── SystemConfig.vue              # 系统配置 + 接口文档
└── utils/constants.js                # 前端常量
```

---

## 🔌 数据接入接口 (供数据处理程序调用)

这是本项目的**核心接口**，上游数据处理程序 (Spark Streaming/Spark SQL) 通过调用这些接口将处理结果写入可视化展示端。

> 📘 完整接口文档请访问 Swagger: `http://localhost:8080/swagger-ui/index.html`

### 1. 交易流水数据接入 `POST /api/data/transaction`

由 **Kafka → Spark Streaming** 实时流处理后调用。对应需求文档 **3.1 节**。

```json
{
  "transId": "TXN20260630120000001",
  "userId": "USER10086",
  "amount": 15000.00,
  "timestamp": 1719734400000,
  "city": "北京",
  "geoLocation": "116.3,39.9",
  "deviceId": "DEVICE_A8F3C2D1",
  "networkType": "4G",
  "devScore": 85
}
```

### 2. 风控告警结果接入 `POST /api/data/alert`

由 **Spark Streaming 实时风控引擎** 判定后调用。对应需求文档 **3.3 节**。

```json
{
  "alertId": "ALT20260630120000001",
  "transId": "TXN20260630120000001",
  "userId": "USER10086",
  "hitRules": "金额突变;异地瞬移",
  "amount": 15000.00,
  "finalScore": 85,
  "riskLevel": "高危",
  "alertLoc": "深圳",
  "geoLocation": "114.05,22.55",
  "networkType": "VPN",
  "devScore": 35,
  "action": "BLOCK",
  "alertTime": 1719734400000
}
```

### 3. 用户画像数据接入 `POST /api/data/profile`

由 **HDFS → Spark SQL 离线批处理** 计算后调用。对应需求文档 **3.2 节**。

```json
{
  "userId": "USER10086",
  "avgAmt30d": 5000.00,
  "commonCities": ["北京", "上海", "广州"],
  "commonDevs": ["DEVICE_A8F3", "DEVICE_B9G4"],
  "lastTransTs": 1719734000000,
  "lastCity": "北京",
  "baseRiskLevel": "低危"
}
```

### 4. 实时指标快照接入 `POST /api/data/metrics`

```json
{
  "snapshotTime": 1719734400000,
  "totalTransactions": 1200,
  "passCount": 800,
  "verifyCount": 300,
  "blockCount": 100,
  "highRiskCount": 100,
  "mediumRiskCount": 300,
  "lowRiskCount": 800,
  "avgRiskScore": 45.5,
  "avgLatency": 350,
  "envRiskCount": 50,
  "amountRiskCount": 80,
  "teleportRiskCount": 15,
  "geoRiskCount": 120
}
```

---

## 📊 前端页面功能

| 页面 | 路由 | 功能说明 |
|------|------|----------|
| **实时监控仪表盘** | `/dashboard` | 6个核心指标卡片 + 交易/告警趋势折线图 + 风险等级饼图 + 规则分类饼图 + 城市柱状图 + 风险速度表 + 最新告警列表 |
| **交易流水监控** | `/transaction` | 统计卡片 + 实时交易流水瀑布表格 (支持VPN/低分设备高亮) |
| **风险告警管理** | `/alerts` | 分页查询 + 筛选(按等级/动作) + 标记处理操作 |
| **风险地理分布** | `/risk-map` | ECharts 中国地图 + 城市散点(涟漪效果) + 城市排行 + 高危详情 |
| **数据统计分析** | `/analysis` | 风险等级饼图 + 规则类型饼图 + 24小时三维趋势对比线 |
| **系统配置** | `/config` | 4个数据接入接口的JSON示例 + 风险评分规则配置表 |

---

## 🔄 多版本说明

### Dev 开发版本 (默认)
- **数据库**: H2 内存数据库 (免安装)
- **Redis**: 可选，服务层已做降级处理（无Redis也能正常运行）
- **模拟数据**: 启动时自动插入7条演示告警数据
- **日志级别**: DEBUG，便于调试
- **启动命令**: `java -jar xxx.jar` (默认激活dev)
- **适用场景**: 本地开发、功能演示、单元测试

### Prod 生产版本
- **数据库**: MySQL 5.7+ (需手动执行 schema.sql)
- **Redis**: 必须 (存储画像缓存和实时数据)
- **连接池**: Druid (最大50连接, SQL防火墙, 慢SQL监控)
- **日志级别**: INFO，日志文件滚动保存
- **启动命令**: `java -jar xxx.jar --spring.profiles.active=prod`
- **适用场景**: 生产部署、性能测试

### Maven Profile 构建
```bash
# 开发版打包
mvn clean package

# 生产版打包
mvn clean package -P prod
```

---

## 🛡️ 软件工程规范

### 分层架构
```
Controller → Service (接口) → ServiceImpl (实现) → DAO (数据访问)
    ↕              ↕
   DTO/VO        Entity
```

### 设计原则
- **单一职责**: 每层职责明确，Controller不包含业务逻辑
- **接口隔离**: Service层定义接口，Impl实现，方便Mock测试
- **依赖倒置**: Controller依赖Service接口，不依赖具体实现
- **DTO/VO分离**: 外部输入用DTO，返回前端用VO，内部流转用Entity

### 代码规范
- **命名**: 遵循阿里巴巴Java开发手册
- **注释**: 所有公开方法和类都有Javadoc注释，中英文双语
- **异常处理**: Service层统一try-catch + log，Controller层统一返回Result
- **隐私脱敏**: 用户ID在VO转换时自动脱敏 (前3后4)
- **参数校验**: 使用 `javax.validation` 注解进行参数校验

### 数据安全
- 用户ID脱敏展示 (`USER****0086`)
- 生产环境关闭H2控制台
- SQL使用参数化查询 (MyBatis预编译)
- CORS配置可限制生产环境允许的域名

---

## 📐 风险评分规则 (对应需求文档 4.2 节)

| 规则名称 | 判定条件 | 分值 | 说明 |
|----------|----------|------|------|
| 金额异常 | `amount > avg_amt_30d × 3` | +30 | 当前消费超历史均值3倍 |
| 地理偏离 | city 不在 common_cities 中 | +20 | 交易城市非常用城市 |
| 异地瞬移 | 位移速度 > 1000km/h | +80 | 坐标变化速度超物理极限 |
| 环境风险 | VPN 或 dev_score < 50 | +40 | 网络或设备环境异常 |

**综合判定**: < 60分→放行 | 60-80分→核验 | > 80分→拦截

---

## 🧪 测试

```bash
# 运行单元测试
cd risk-dashboard-backend
mvn test

# 测试覆盖:
# ✓ 交易流水数据接收
# ✓ 告警数据接收
# ✓ 仪表盘数据聚合
# ✓ 批量数据接收
```

---

## 📞 技术栈与依赖

| 组件 | 技术 | 版本 |
|------|------|------|
| 后端框架 | Spring Boot | 2.7.18 |
| Java版本 | Java | 1.8 |
| ORM | MyBatis | 2.3.1 |
| 连接池 | Druid | 1.2.20 |
| 缓存 | Redis (Lettuce) | - |
| 序列化 | FastJSON | 2.0.43 |
| API文档 | Swagger 3 | 3.0.0 |
| 前端框架 | Vue | 2.7.16 |
| 图表库 | ECharts | 5.5.0 |
| UI组件 | Element UI | 2.15.14 |
| HTTP客户端 | Axios | 1.6.0 |

---

## 📄 License

本项目为金融交易风险实时监控平台的教学/演示版本，仅供学习参考。
