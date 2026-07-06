package com.finance.risk.dashboard.dao;

import com.finance.risk.dashboard.entity.UserProfile;
import org.apache.ibatis.annotations.*;

@Mapper
public interface UserProfileDao {

    @Insert("INSERT INTO user_profile(user_id, avg_amt_30d, common_cities, common_devs, " +
            "common_pay_channels, common_trans_types, common_counterparties, " +
            "last_trans_ts, last_city, last_ip, last_login_time, " +
            "registration_time, total_balance, single_limit, daily_limit, monthly_limit, " +
            "account_status, login_count_24h, trans_count_24h, trans_amount_24h, trans_count_7d, " +
            "cancel_retry_count, risk_tags, risk_score) " +
            "VALUES(#{userId}, #{avgAmt30d}, #{commonCities}, #{commonDevs}, " +
            "#{commonPayChannels}, #{commonTransTypes}, #{commonCounterparties}, " +
            "#{lastTransTs}, #{lastCity}, #{lastIp}, #{lastLoginTime}, " +
            "#{registrationTime}, #{totalBalance}, #{singleLimit}, #{dailyLimit}, #{monthlyLimit}, " +
            "#{accountStatus}, #{loginCount24h}, #{transCount24h}, #{transAmount24h}, #{transCount7d}, " +
            "#{cancelRetryCount}, #{riskTags}, #{riskScore})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(UserProfile profile);

    @Update("UPDATE user_profile SET avg_amt_30d = #{avgAmt30d}, " +
            "common_cities = #{commonCities}, common_devs = #{commonDevs}, " +
            "common_pay_channels = #{commonPayChannels}, common_trans_types = #{commonTransTypes}, " +
            "common_counterparties = #{commonCounterparties}, " +
            "last_trans_ts = #{lastTransTs}, last_city = #{lastCity}, " +
            "last_ip = #{lastIp}, last_login_time = #{lastLoginTime}, " +
            "registration_time = #{registrationTime}, total_balance = #{totalBalance}, " +
            "single_limit = #{singleLimit}, daily_limit = #{dailyLimit}, monthly_limit = #{monthlyLimit}, " +
            "account_status = #{accountStatus}, login_count_24h = #{loginCount24h}, " +
            "trans_count_24h = #{transCount24h}, trans_amount_24h = #{transAmount24h}, " +
            "trans_count_7d = #{transCount7d}, cancel_retry_count = #{cancelRetryCount}, " +
            "risk_tags = #{riskTags}, risk_score = #{riskScore} " +
            "WHERE user_id = #{userId}")
    int updateByUserId(UserProfile profile);

    @Select("SELECT * FROM user_profile WHERE user_id = #{userId}")
    UserProfile findByUserId(@Param("userId") String userId);

    @Delete("DELETE FROM user_profile WHERE user_id = #{userId}")
    int deleteByUserId(@Param("userId") String userId);
}
