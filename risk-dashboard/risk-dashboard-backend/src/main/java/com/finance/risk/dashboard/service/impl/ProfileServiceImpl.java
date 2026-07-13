package com.finance.risk.dashboard.service.impl;

import com.alibaba.fastjson.JSON;
import com.finance.risk.dashboard.common.Constants;
import com.finance.risk.dashboard.dao.UserProfileDao;
import com.finance.risk.dashboard.dto.ProfileInputDTO;
import com.finance.risk.dashboard.entity.UserProfile;
import com.finance.risk.dashboard.service.ProfileService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * 用户画像服务实现 — MySQL 持久化 + Redis 缓存双写
 */
@Service
public class ProfileServiceImpl implements ProfileService {

    private static final Logger log = LoggerFactory.getLogger(ProfileServiceImpl.class);

    @Resource
    private UserProfileDao userProfileDao;

    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    private static final long PROFILE_CACHE_HOURS = 24;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean receiveProfile(ProfileInputDTO dto) {
        if (dto == null) {
            log.warn("用户画像数据为空，跳过处理");
            return false;
        }

        UserProfile profile = convertToEntity(dto);

        // 持久化到 MySQL
        UserProfile existing = userProfileDao.findByUserId(profile.getUserId());
        if (existing != null) {
            userProfileDao.updateByUserId(profile);
            log.info("用户画像更新成功: userId={}", profile.getUserId());
        } else {
            userProfileDao.insert(profile);
            log.info("用户画像新增成功: userId={}", profile.getUserId());
        }

        // 同步写入 Redis 缓存
        updateProfileCache(profile);
        return true;
    }

    @Override
    public UserProfile getProfileByUserId(String userId) {
        if (userId == null || userId.isEmpty()) {
            return null;
        }

        String cacheKey = Constants.REDIS_PROFILE_PREFIX + userId;

        try {
            Object cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached != null) {
                return JSON.parseObject(cached.toString(), UserProfile.class);
            }
        } catch (Exception e) {
            log.error("Redis读取用户画像失败: userId={}, error={}", userId, e.getMessage());
        }

        // Redis 未命中，回退 MySQL
        UserProfile profile = userProfileDao.findByUserId(userId);
        if (profile != null) {
            updateProfileCache(profile);
        }
        return profile;
    }

    @Override
    public void updateProfileCache(UserProfile profile) {
        try {
            String cacheKey = Constants.REDIS_PROFILE_PREFIX + profile.getUserId();
            redisTemplate.opsForValue().set(cacheKey, JSON.toJSONString(profile),
                    PROFILE_CACHE_HOURS, TimeUnit.HOURS);
        } catch (Exception e) {
            log.error("用户画像缓存更新失败: userId={}, error={}",
                    profile.getUserId(), e.getMessage());
        }
    }

    @Override
    public Map<String, Object> queryProfileList(String accountStatus, String riskLevel, int page, int pageSize) {
        int offset = (page - 1) * pageSize;
        List<UserProfile> list = userProfileDao.findListByFilter(accountStatus, riskLevel, offset, pageSize);
        Long total = userProfileDao.countByFilter(accountStatus, riskLevel);
        Map<String, Object> result = new HashMap<>();
        result.put("list", list); result.put("total", total);
        result.put("page", page); result.put("pageSize", pageSize);
        return result;
    }

    @Override
    public Map<String, Object> getProfileStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalUsers", userProfileDao.count());
        List<UserProfileDao.StatusCount> scs = userProfileDao.countByStatus();
        long normal = 0, frozen = 0, flagged = 0, dormant = 0;
        for (UserProfileDao.StatusCount sc : scs) {
            if ("normal".equals(sc.getAccountStatus())) normal = sc.getCnt();
            else if ("frozen".equals(sc.getAccountStatus())) frozen = sc.getCnt();
            else if ("flagged".equals(sc.getAccountStatus())) flagged = sc.getCnt();
            else if ("dormant".equals(sc.getAccountStatus())) dormant = sc.getCnt();
        }
        stats.put("normal", normal); stats.put("frozen", frozen);
        stats.put("flagged", flagged); stats.put("dormant", dormant);
        return stats;
    }

    private UserProfile convertToEntity(ProfileInputDTO dto) {
        return UserProfile.builder()
                .userId(dto.getUserId())
                .avgAmt30d(dto.getAvgAmt30d())
                .commonCities(dto.getCommonCities())
                .commonDevs(dto.getCommonDevs())
                .commonPayChannels(dto.getCommonPayChannels())
                .commonTransTypes(dto.getCommonTransTypes())
                .commonCounterparties(dto.getCommonCounterparties())
                .lastTransTs(dto.getLastTransTs())
                .lastCity(dto.getLastCity())
                .lastIp(dto.getLastIp())
                .lastLoginTime(dto.getLastLoginTime())
                .registrationTime(dto.getRegistrationTime())
                .totalBalance(dto.getTotalBalance())
                .singleLimit(dto.getSingleLimit())
                .dailyLimit(dto.getDailyLimit())
                .monthlyLimit(dto.getMonthlyLimit())
                .accountStatus(dto.getAccountStatus())
                .loginCount24h(dto.getLoginCount24h())
                .transCount24h(dto.getTransCount24h())
                .transAmount24h(dto.getTransAmount24h())
                .transCount7d(dto.getTransCount7d())
                .cancelRetryCount(dto.getCancelRetryCount())
                .riskTags(dto.getRiskTags())
                .riskScore(dto.getRiskScore())
                .build();
    }
}
