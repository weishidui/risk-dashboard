package com.finance.risk.dashboard.service;

import com.finance.risk.dashboard.dto.AlertInputDTO;
import com.finance.risk.dashboard.vo.AlertVO;

import java.util.List;
import java.util.Map;

public interface AlertService {

    int receiveAlerts(List<AlertInputDTO> alerts);

    boolean receiveAlert(AlertInputDTO alert);

    Map<String, Object> queryAlertList(String riskLevel, String status, String startTime, String endTime,
                                        int page, int pageSize);

    /** 更新告警处理状态 (工作流) */
    boolean updateAlertStatus(String alertId, String status, String handler, String remark);

    List<AlertVO> getRecentAlerts(int limit);

    List<AlertVO> getRecentSevereAlerts(int limit);

    List<Map<String, Object>> countByRiskLevel();

    List<Map<String, Object>> countByHitRule();

    List<Map<String, Object>> countHighRiskByCity(int limit);

    /** 按规则类别(A-I)统计命中次数 */
    List<Map<String, Object>> countByCategory();
}
