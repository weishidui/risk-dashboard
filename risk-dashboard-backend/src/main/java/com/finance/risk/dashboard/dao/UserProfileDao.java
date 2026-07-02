package com.finance.risk.dashboard.dao;

import com.finance.risk.dashboard.entity.UserProfile;
import org.apache.ibatis.annotations.*;

/**
 * 用户画像数据访问层 — 对应 user_profile 表
 */
@Mapper
public interface UserProfileDao {

    @Insert("INSERT INTO user_profile(user_id, avg_amt_30d, common_cities, common_devs, " +
            "last_trans_ts, last_city) " +
            "VALUES(#{userId}, #{avgAmt30d}, #{commonCities}, #{commonDevs}, " +
            "#{lastTransTs}, #{lastCity})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(UserProfile profile);

    @Update("UPDATE user_profile SET avg_amt_30d = #{avgAmt30d}, " +
            "common_cities = #{commonCities}, common_devs = #{commonDevs}, " +
            "last_trans_ts = #{lastTransTs}, last_city = #{lastCity} " +
            "WHERE user_id = #{userId}")
    int updateByUserId(UserProfile profile);

    @Select("SELECT * FROM user_profile WHERE user_id = #{userId}")
    UserProfile findByUserId(@Param("userId") String userId);

    @Delete("DELETE FROM user_profile WHERE user_id = #{userId}")
    int deleteByUserId(@Param("userId") String userId);
}
