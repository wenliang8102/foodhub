package com.foodhub.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.foodhub.auth.entity.UserEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserMapper extends BaseMapper<UserEntity> {
}

