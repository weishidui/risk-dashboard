package com.finance.risk.dashboard.service.impl;

import com.finance.risk.dashboard.common.Constants;
import com.finance.risk.dashboard.common.JwtUtil;
import com.finance.risk.dashboard.dao.SysUserDao;
import com.finance.risk.dashboard.dto.LoginDTO;
import com.finance.risk.dashboard.dto.RegisterDTO;
import com.finance.risk.dashboard.entity.SysUser;
import com.finance.risk.dashboard.service.SysUserService;
import com.finance.risk.dashboard.vo.LoginVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class SysUserServiceImpl implements SysUserService {

    private static final Logger log = LoggerFactory.getLogger(SysUserServiceImpl.class);
    private static final Set<String> VALID_ROLES = new HashSet<>(Arrays.asList(
            Constants.ROLE_TRADER, Constants.ROLE_REALTIME_ANALYST,
            Constants.ROLE_OFFLINE_ANALYST, Constants.ROLE_ADMIN, Constants.ROLE_AUDITOR));

    @Resource
    private SysUserDao sysUserDao;

    @Override
    public LoginVO login(LoginDTO dto) {
        SysUser user = sysUserDao.findByUsername(dto.getUsername());
        if (user == null || !user.getPassword().equals(dto.getPassword())) {
            throw new RuntimeException("用户名或密码错误");
        }
        String token = JwtUtil.generateToken(user.getUsername(), user.getRole());
        log.info("用户登录: username={}, role={}", user.getUsername(), user.getRole());
        return LoginVO.builder()
                .token(token)
                .user(LoginVO.UserInfo.builder()
                        .username(user.getUsername())
                        .role(user.getRole())
                        .build())
                .build();
    }

    @Override
    public LoginVO register(RegisterDTO dto) {
        if (!VALID_ROLES.contains(dto.getRole())) {
            throw new RuntimeException("无效的角色: " + dto.getRole());
        }
        SysUser existing = sysUserDao.findByUsername(dto.getUsername());
        if (existing != null) {
            throw new RuntimeException("用户名已存在");
        }
        SysUser user = SysUser.builder()
                .username(dto.getUsername())
                .password(dto.getPassword())
                .role(dto.getRole())
                .build();
        sysUserDao.insert(user);
        String token = JwtUtil.generateToken(user.getUsername(), user.getRole());
        log.info("用户注册: username={}, role={}", user.getUsername(), user.getRole());
        return LoginVO.builder()
                .token(token)
                .user(LoginVO.UserInfo.builder()
                        .username(user.getUsername())
                        .role(user.getRole())
                        .build())
                .build();
    }

    @Override
    public List<SysUser> listUsers() { return sysUserDao.listAll(); }

    @Override
    public boolean deleteUser(String username) {
        if ("admin".equals(username)) throw new RuntimeException("不能删除默认管理员");
        return sysUserDao.deleteByUsername(username) > 0;
    }

    @Override
    public boolean resetPassword(String username, String newPassword) {
        return sysUserDao.updatePassword(username, newPassword) > 0;
    }
}
