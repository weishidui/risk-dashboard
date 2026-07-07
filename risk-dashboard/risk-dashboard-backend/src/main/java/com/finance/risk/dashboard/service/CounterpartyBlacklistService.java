package com.finance.risk.dashboard.service;

import com.finance.risk.dashboard.entity.CounterpartyBlacklist;

import java.util.List;
import java.util.Map;

public interface CounterpartyBlacklistService {

    Map<String, Object> queryList(int page, int pageSize);

    Map<String, Object> queryByRiskLevel(String riskLevel, int page, int pageSize);

    List<Map<String, Object>> countByRiskLevel();

    CounterpartyBlacklist getByCounterpartyId(String counterpartyId);
}
