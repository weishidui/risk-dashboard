package com.finance.risk.dashboard;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 金融交易风险实时监控平台 - 可视化展示端
 *
 * <p>基于 Spring Boot 2.7 + Java 1.8 构建</p>
 *
 * <p>核心职责：</p>
 * <ul>
 *   <li>接收上游数据处理程序 (Spark Streaming/Spark SQL) 的处理结果</li>
 *   <li>提供 REST API 供前端 Vue 大屏查询展示</li>
 *   <li>通过 WebSocket 向前端实时推送最新风险数据</li>
 *   <li>管理 MySQL 结果存储和 Redis 缓存</li>
 * </ul>
 *
 * @author Risk Dashboard Team
 * @since 1.0.0
 */
@SpringBootApplication
@MapperScan("com.finance.risk.dashboard.dao")
@EnableScheduling
public class RiskDashboardApplication {

    public static void main(String[] args) {
        SpringApplication.run(RiskDashboardApplication.class, args);
        System.out.println("\n============================================================");
        System.out.println("  金融交易风险实时监控平台 - 可视化展示端");
        System.out.println("  Version: 1.0.0 | Java: 1.8 | Spring Boot: 2.7.x");
        System.out.println("  Swagger: http://localhost:8080/swagger-ui/index.html");
        System.out.println("  H2 Console (dev): http://localhost:8080/h2-console");
        System.out.println("============================================================\n");
    }
}
