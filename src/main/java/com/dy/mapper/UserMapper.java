package com.dy.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dy.entity.User;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserMapper extends BaseMapper<User> {
}
