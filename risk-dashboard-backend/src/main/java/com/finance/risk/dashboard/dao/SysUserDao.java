package com.finance.risk.dashboard.dao;

import com.finance.risk.dashboard.entity.SysUser;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface SysUserDao {

    @Insert("INSERT INTO sys_user(username, password, role) VALUES(#{username}, #{password}, #{role})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(SysUser user);

    @Select("SELECT * FROM sys_user WHERE username = #{username}")
    SysUser findByUsername(@Param("username") String username);

    @Select("SELECT id, username, role, create_time FROM sys_user ORDER BY create_time DESC")
    List<SysUser> listAll();

    @Delete("DELETE FROM sys_user WHERE username = #{username}")
    int deleteByUsername(@Param("username") String username);

    @Update("UPDATE sys_user SET password = #{password} WHERE username = #{username}")
    int updatePassword(@Param("username") String username, @Param("password") String password);
}
