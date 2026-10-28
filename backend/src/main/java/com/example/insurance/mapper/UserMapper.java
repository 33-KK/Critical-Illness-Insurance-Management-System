package com.example.insurance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.insurance.Domain.User;
import org.apache.ibatis.annotations.Select;

public interface UserMapper extends BaseMapper<User> {
  @Select("SELECT * FROM app_user WHERE id = #{id} FOR UPDATE")
  User lockById(Long id);

  @Select("SELECT * FROM app_user WHERE username = #{username} FOR UPDATE")
  User lockByUsername(String username);
}
