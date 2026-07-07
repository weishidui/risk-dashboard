package com.finance.risk.dashboard.service;

import com.finance.risk.dashboard.dto.ProfileInputDTO;
import com.finance.risk.dashboard.entity.UserProfile;

/**
 * 用户画像服务接口 — MySQL + Redis 双写
 */
public interface ProfileService {

    boolean receiveProfile(ProfileInputDTO profile);

    UserProfile getProfileByUserId(String userId);

    void updateProfileCache(UserProfile profile);
}
