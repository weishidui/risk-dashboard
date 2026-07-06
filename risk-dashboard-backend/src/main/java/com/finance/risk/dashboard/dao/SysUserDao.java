package com.finance.risk.dashboard.dao;

import com.finance.risk.dashboard.entity.SysUser;
import org.apache.ibatis.annotations.*;

@Mapper
public interface SysUserDao {

    @Insert("INSERT INTO sys_user(username, password, role) VALUES(#{username}, #{password}, #{role})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(SysUser user);

    @Select("SELECT * FROM sys_user WHERE username = #{username}")
    SysUser findByUsername(@Param("username") String username);
}
