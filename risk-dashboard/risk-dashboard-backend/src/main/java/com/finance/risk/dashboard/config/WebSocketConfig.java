package com.finance.risk.dashboard.config;

import com.finance.risk.dashboard.websocket.RiskWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import javax.annotation.Resource;

/**
 * WebSocket 配置
 * 注册风险数据实时推送处理器
 *
 * @author Risk Dashboard Team
 * @since 1.0.0
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    @Resource
    private RiskWebSocketHandler riskWebSocketHandler;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(riskWebSocketHandler, "/ws/risk")
                .setAllowedOriginPatterns("*");
    }
}
