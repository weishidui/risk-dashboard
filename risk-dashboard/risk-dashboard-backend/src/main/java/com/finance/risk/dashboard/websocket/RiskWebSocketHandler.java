package com.finance.risk.dashboard.websocket;

import com.alibaba.fastjson.JSON;
import com.finance.risk.dashboard.common.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 风险数据 WebSocket 实时推送处理器
 * 向前端大屏实时推送最新告警和交易数据
 *
 * @author Risk Dashboard Team
 * @since 1.0.0
 */
@Component
public class RiskWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(RiskWebSocketHandler.class);

    /** 在线会话集合 (线程安全) */
    private static final Map<String, WebSocketSession> SESSIONS = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String sessionId = session.getId();
        SESSIONS.put(sessionId, session);
        log.info("WebSocket 连接建立: sessionId={}, 当前在线数={}", sessionId, SESSIONS.size());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        // 接收前端心跳或订阅请求
        String payload = message.getPayload();
        log.debug("收到客户端消息: sessionId={}, message={}", session.getId(), payload);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String sessionId = session.getId();
        SESSIONS.remove(sessionId);
        log.info("WebSocket 连接断开: sessionId={}, status={}, 当前在线数={}",
                sessionId, status, SESSIONS.size());
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.error("WebSocket 传输异常: sessionId={}", session.getId(), exception);
        SESSIONS.remove(session.getId());
    }

    // ==================== 广播推送方法 ====================

    /**
     * 广播实时告警数据到所有前端客户端
     *
     * @param alertJson 告警数据JSON
     */
    public void broadcastAlert(String alertJson) {
        broadcast(Constants.WS_TOPIC_ALERT, alertJson);
    }

    /**
     * 广播实时交易数据
     *
     * @param transactionJson 交易数据JSON
     */
    public void broadcastTransaction(String transactionJson) {
        broadcast(Constants.WS_TOPIC_TRANSACTION, transactionJson);
    }

    /**
     * 广播指标更新
     *
     * @param metricsJson 指标数据JSON
     */
    public void broadcastMetrics(String metricsJson) {
        broadcast(Constants.WS_TOPIC_METRICS, metricsJson);
    }

    /**
     * 广播消息到所有已连接的客户端
     */
    private void broadcast(String topic, String message) {
        if (SESSIONS.isEmpty()) {
            return;
        }

        // 构建推送消息
        Map<String, String> pushMessage = new java.util.HashMap<>();
        pushMessage.put("topic", topic);
        pushMessage.put("data", message);
        pushMessage.put("timestamp", String.valueOf(System.currentTimeMillis()));

        String payload = JSON.toJSONString(pushMessage);
        TextMessage textMessage = new TextMessage(payload);

        int successCount = 0;
        for (WebSocketSession session : SESSIONS.values()) {
            if (session.isOpen()) {
                try {
                    synchronized (session) {
                        session.sendMessage(textMessage);
                    }
                    successCount++;
                } catch (IOException e) {
                    log.error("WebSocket 消息发送失败: sessionId={}", session.getId());
                }
            }
        }

        if (successCount > 0) {
            log.debug("WebSocket 广播完成: topic={}, 推送成功={}/{}",
                    topic, successCount, SESSIONS.size());
        }
    }

    /**
     * 获取当前在线连接数
     */
    public int getOnlineCount() {
        return SESSIONS.size();
    }
}
