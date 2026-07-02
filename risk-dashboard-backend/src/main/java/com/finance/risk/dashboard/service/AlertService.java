package com.finance.risk.dashboard.service;

import com.finance.risk.dashboard.dto.AlertInputDTO;
import com.finance.risk.dashboard.vo.AlertVO;

import java.util.List;
import java.util.Map;

/**
 * 风控告警服务接口
 */
public interface AlertService {

    int receiveAlerts(List<AlertInputDTO> alerts);

    boolean receiveAlert(AlertInputDTO alert);

    Map<String, Object> queryAlertList(String riskLevel, String startTime, String endTime,
                                        int page, int pageSize);

    List<AlertVO> getRecentAlerts(int limit);

    List<Map<String, Object>> countByRiskLevel();

    List<Map<String, Object>> countByHitRule();

    List<Map<String, Object>> countHighRiskByCity(int limit);
}
