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

    private UserProfile convertToEntity(ProfileInputDTO dto) {
        return UserProfile.builder()
                .userId(dto.getUserId())
                .avgAmt30d(dto.getAvgAmt30d())
                .commonCities(dto.getCommonCities())
                .commonDevs(dto.getCommonDevs())
                .lastTransTs(dto.getLastTransTs())
                .lastCity(dto.getLastCity())
                .build();
    }
}
