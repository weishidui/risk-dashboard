package com.finance.risk.dashboard.service;

import com.finance.risk.dashboard.entity.TransChain;

import java.util.List;
import java.util.Map;

public interface TransChainService {

    Map<String, Object> queryList(int page, int pageSize);

    List<TransChain> queryByChainId(String chainId);

    List<TransChain> queryLoopChains(int limit);

    Map<String, Object> getStats();
}
