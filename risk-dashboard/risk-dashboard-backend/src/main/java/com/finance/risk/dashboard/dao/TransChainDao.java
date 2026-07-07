package com.finance.risk.dashboard.dao;

import com.finance.risk.dashboard.entity.TransChain;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface TransChainDao {

    @Select("SELECT * FROM trans_chain WHERE chain_id = #{chainId} ORDER BY hop_order")
    List<TransChain> findByChainId(@Param("chainId") String chainId);

    @Select("SELECT * FROM trans_chain WHERE from_user_id = #{userId} OR to_user_id = #{userId} " +
            "ORDER BY create_time DESC LIMIT #{limit}")
    List<TransChain> findByUserId(@Param("userId") String userId, @Param("limit") int limit);

    @Select("SELECT * FROM trans_chain WHERE is_loop = 1 ORDER BY create_time DESC LIMIT #{limit}")
    List<TransChain> findLoopChains(@Param("limit") int limit);

    @Select("SELECT * FROM trans_chain ORDER BY create_time DESC LIMIT #{offset}, #{limit}")
    List<TransChain> findList(@Param("offset") int offset, @Param("limit") int limit);

    @Select("SELECT COUNT(*) FROM trans_chain")
    Long count();

    @Select("SELECT chain_depth, COUNT(DISTINCT chain_id) as cnt FROM trans_chain " +
            "GROUP BY chain_depth ORDER BY chain_depth")
    List<DepthCount> countByDepth();

    @Select("SELECT COUNT(DISTINCT chain_id) as cnt FROM trans_chain WHERE is_loop = 1")
    Long countLoopChains();

    class DepthCount {
        private Integer chainDepth;
        private Long cnt;
        public Integer getChainDepth() { return chainDepth; }
        public void setChainDepth(Integer chainDepth) { this.chainDepth = chainDepth; }
        public Long getCnt() { return cnt; }
        public void setCnt(Long cnt) { this.cnt = cnt; }
    }
}
