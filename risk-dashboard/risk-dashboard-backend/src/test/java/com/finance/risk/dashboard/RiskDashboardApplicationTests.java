package com.finance.risk.dashboard;

import com.finance.risk.dashboard.dto.AlertInputDTO;
import com.finance.risk.dashboard.dto.TransactionInputDTO;
import com.finance.risk.dashboard.service.AlertService;
import com.finance.risk.dashboard.service.MetricsService;
import com.finance.risk.dashboard.service.TransactionService;
import com.finance.risk.dashboard.vo.DashboardVO;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 系统集成测试
 *
 * @author Risk Dashboard Team
 * @since 1.0.0
 */
@SpringBootTest
class RiskDashboardApplicationTests {

    @Resource
    private TransactionService transactionService;

    @Resource
    private AlertService alertService;

    @Resource
    private MetricsService metricsService;

    /**
     * 测试上下文加载
     */
    @Test
    void contextLoads() {
        System.out.println("Spring Boot 上下文加载成功");
    }

    /**
     * 测试交易流水数据接收
     */
    @Test
    void testTransactionReceive() {
        TransactionInputDTO txn = new TransactionInputDTO();
        txn.setTransId("TEST_TXN_" + System.currentTimeMillis());
        txn.setUserId("USER_TEST");
        txn.setAmount(1000.0);
        txn.setTimestamp(System.currentTimeMillis());
        txn.setCity("北京");
        txn.setGeoLocation("116.3,39.9");
        txn.setDeviceId("DEV_TEST");
        txn.setNetworkType("4G");
        txn.setDevScore(85);

        boolean success = transactionService.receiveTransaction(txn);
        assertTrue(success, "交易流水接收应该成功");
        System.out.println("✓ 交易流水接收测试通过");
    }

    /**
     * 测试告警数据接收
     */
    @Test
    void testAlertReceive() {
        AlertInputDTO alert = new AlertInputDTO();
        alert.setAlertId("TEST_ALT_" + System.currentTimeMillis());
        alert.setTransId("TEST_TXN_ALT");
        alert.setUserId("USER_TEST");
        alert.setHitRules("金额突变;环境风险");
        alert.setAmount(30000.0);
        alert.setFinalScore(85);
        alert.setRiskLevel("高危");
        alert.setAlertLoc("深圳");
        alert.setCity("深圳");
        alert.setStatus("pending");
        alert.setCounterpartyId("CP_TEST");
        alert.setIpAddress("192.168.1.1");
        alert.setIsNewDevice(1);
        alert.setIsNewCounterparty(1);

        boolean success = alertService.receiveAlert(alert);
        assertTrue(success, "告警数据接收应该成功");
        System.out.println("✓ 告警数据接收测试通过");
    }

    /**
     * 测试仪表盘数据聚合
     */
    @Test
    void testDashboardData() {
        DashboardVO dashboard = metricsService.getDashboardData();
        assertNotNull(dashboard, "仪表盘数据不应为空");
        assertNotNull(dashboard.getRiskLevelDistribution(), "风险分布不应为空");
        assertNotNull(dashboard.getRecentAlerts(), "最新告警不应为空");
        System.out.println("✓ 仪表盘数据聚合测试通过");
        System.out.println("  - 风险等级分布: " + dashboard.getRiskLevelDistribution().size() + " 项");
        System.out.println("  - 最近告警: " + dashboard.getRecentAlerts().size() + " 条");
    }

    /**
     * 测试批量接收交易数据
     */
    @Test
    void testBatchTransactionReceive() {
        List<TransactionInputDTO> list = Arrays.asList(
                createTestTxn("BATCH_001", "USER_A", 5000.0, "北京"),
                createTestTxn("BATCH_002", "USER_B", 8000.0, "上海"),
                createTestTxn("BATCH_003", "USER_C", 12000.0, "深圳")
        );

        int count = transactionService.receiveTransactions(list);
        assertEquals(3, count, "批量插入应返回3");
        System.out.println("✓ 批量交易接收测试通过: " + count + " 条");
    }

    private TransactionInputDTO createTestTxn(String transId, String userId,
                                               Double amount, String city) {
        TransactionInputDTO txn = new TransactionInputDTO();
        txn.setTransId(transId);
        txn.setUserId(userId);
        txn.setAmount(amount);
        txn.setTimestamp(System.currentTimeMillis());
        txn.setCity(city);
        txn.setGeoLocation("116.3,39.9");
        txn.setDeviceId("DEV_" + userId);
        txn.setNetworkType("4G");
        txn.setDevScore(80);
        return txn;
    }
}
