package com.finance.risk.dashboard.service;

import com.finance.risk.dashboard.dto.LoginDTO;
import com.finance.risk.dashboard.dto.RegisterDTO;
import com.finance.risk.dashboard.entity.SysUser;
import com.finance.risk.dashboard.vo.LoginVO;

import java.util.List;

public interface SysUserService {
    LoginVO login(LoginDTO dto);
    LoginVO register(RegisterDTO dto);
    List<SysUser> listUsers();
    boolean deleteUser(String username);
    boolean resetPassword(String username, String newPassword);
}
