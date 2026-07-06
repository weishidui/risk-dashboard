package com.finance.risk.dashboard.service;

import com.finance.risk.dashboard.dto.LoginDTO;
import com.finance.risk.dashboard.dto.RegisterDTO;
import com.finance.risk.dashboard.vo.LoginVO;

public interface SysUserService {
    LoginVO login(LoginDTO dto);
    LoginVO register(RegisterDTO dto);
}
