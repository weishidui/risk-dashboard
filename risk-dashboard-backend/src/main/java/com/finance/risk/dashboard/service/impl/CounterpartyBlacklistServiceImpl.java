package com.finance.risk.dashboard.service.impl;

import com.finance.risk.dashboard.dao.CounterpartyBlacklistDao;
import com.finance.risk.dashboard.entity.CounterpartyBlacklist;
import com.finance.risk.dashboard.service.CounterpartyBlacklistService;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class CounterpartyBlacklistServiceImpl implements CounterpartyBlacklistService {

    @Resource
    private CounterpartyBlacklistDao dao;

    @Override
    public Map<String, Object> queryList(int page, int pageSize) {
        int offset = (page - 1) * pageSize;
        List<CounterpartyBlacklist> list = dao.findList(offset, pageSize);
        Long total = dao.count();

        Map<String, Object> result = new HashMap<>();
        result.put("list", list);
        result.put("total", total);
        result.put("page", page);
        result.put("pageSize", pageSize);
        return result;
    }

    @Override
    public Map<String, Object> queryByRiskLevel(String riskLevel, int page, int pageSize) {
        int offset = (page - 1) * pageSize;
        List<CounterpartyBlacklist> list = dao.findByRiskLevel(riskLevel, offset, pageSize);
        Long total = dao.count();

        Map<String, Object> result = new HashMap<>();
        result.put("list", list);
        result.put("total", total);
        result.put("page", page);
        result.put("pageSize", pageSize);
        return result;
    }

    @Override
    public List<Map<String, Object>> countByRiskLevel() {
        List<CounterpartyBlacklistDao.RiskLevelCount> counts = dao.countByRiskLevel(null);
        return counts.stream().map(c -> {
            Map<String, Object> map = new HashMap<>();
            map.put("name", c.getRiskLevel());
            map.put("value", c.getCnt());
            return map;
        }).collect(Collectors.toList());
    }

    @Override
    public CounterpartyBlacklist getByCounterpartyId(String counterpartyId) {
        return dao.findByCounterpartyId(counterpartyId);
    }
}
