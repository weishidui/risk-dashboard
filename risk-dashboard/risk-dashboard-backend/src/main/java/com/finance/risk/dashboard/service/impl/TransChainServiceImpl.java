package com.finance.risk.dashboard.service.impl;

import com.finance.risk.dashboard.dao.TransChainDao;
import com.finance.risk.dashboard.entity.TransChain;
import com.finance.risk.dashboard.service.TransChainService;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class TransChainServiceImpl implements TransChainService {

    @Resource
    private TransChainDao dao;

    @Override
    public Map<String, Object> queryList(int page, int pageSize) {
        int offset = (page - 1) * pageSize;
        List<TransChain> list = dao.findList(offset, pageSize);
        Long total = dao.count();

        Map<String, Object> result = new HashMap<>();
        result.put("list", list);
        result.put("total", total);
        result.put("page", page);
        result.put("pageSize", pageSize);
        return result;
    }

    @Override
    public List<TransChain> queryByChainId(String chainId) {
        return dao.findByChainId(chainId);
    }

    @Override
    public List<TransChain> queryLoopChains(int limit) {
        return dao.findLoopChains(limit);
    }

    @Override
    public Map<String, Object> getStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalChains", dao.count());
        stats.put("loopChains", dao.countLoopChains());
        stats.put("depthDistribution", dao.countByDepth().stream().map(d -> {
            Map<String, Object> m = new HashMap<>();
            m.put("depth", d.getChainDepth());
            m.put("count", d.getCnt());
            return m;
        }).collect(Collectors.toList()));
        return stats;
    }
}
