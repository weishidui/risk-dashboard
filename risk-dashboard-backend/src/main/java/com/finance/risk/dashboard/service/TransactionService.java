package com.finance.risk.dashboard.service;

import com.finance.risk.dashboard.dto.TransactionInputDTO;
import com.finance.risk.dashboard.entity.Transaction;
import com.finance.risk.dashboard.vo.DistributionVO;

import java.util.List;
import java.util.Map;

/**
 * 交易流水服务接口
 *
 * @author Risk Dashboard Team
 * @since 1.0.0
 */
public interface TransactionService {

    /**
     * 接收数据处理结果：批量写入交易流水
     *
     * @param transactions 交易数据列表
     * @return 成功写入数量
     */
    int receiveTransactions(List<TransactionInputDTO> transactions);

    /**
     * 接收单条交易流水
     *
     * @param transaction 交易数据
     * @return 是否成功
     */
    boolean receiveTransaction(TransactionInputDTO transaction);

    /**
     * 获取最近交易流水 (用于瀑布流展示)
     *
     * @param limit 数量
     * @return 交易列表
     */
    List<Transaction> getRecentTransactions(int limit);

    /**
     * 按城市统计交易量
     *
     * @param limit 数量限制
     * @return 城市分布
     */
    List<DistributionVO> countByCity(int limit);

    /**
     * 统计时间段内交易量
     */
    Long countByTimeRange(Long startTime, Long endTime);
}
