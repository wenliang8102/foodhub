package com.foodhub.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.foodhub.auth.dto.LoginRequest;
import com.foodhub.auth.dto.RegisterRequest;
import com.foodhub.auth.entity.UserEntity;
import com.foodhub.auth.mapper.UserMapper;
import com.foodhub.common.security.JwtTokenService;
import com.foodhub.auth.vo.AuthResponse;
import com.foodhub.auth.vo.UserProfile;
import com.foodhub.common.core.BusinessException;
import com.foodhub.common.redis.RedisKeys;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class AuthService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;
    private final StringRedisTemplate redisTemplate;

    public AuthService(UserMapper userMapper,
                       PasswordEncoder passwordEncoder,
                       JwtTokenService jwtTokenService,
                       StringRedisTemplate redisTemplate) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenService = jwtTokenService;
        this.redisTemplate = redisTemplate;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        LambdaQueryWrapper<UserEntity> accountQuery = new LambdaQueryWrapper<UserEntity>()
                .eq(UserEntity::getUsername, request.username());
        if (request.phone() != null && !request.phone().isBlank()) {
            accountQuery.or(wrapper -> wrapper.eq(UserEntity::getPhone, request.phone()));
        }
        if (request.email() != null && !request.email().isBlank()) {
            accountQuery.or(wrapper -> wrapper.eq(UserEntity::getEmail, request.email()));
        }
        boolean exists = userMapper.selectCount(accountQuery) > 0;
        if (exists) {
            throw new BusinessException("ACCOUNT_EXISTS", "Username, phone, or email is already registered");
        }

        UserEntity user = new UserEntity();
        user.setUsername(request.username());
        user.setPhone(request.phone());
        user.setEmail(request.email());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRole("USER");
        user.setStatus(1);
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        try {
            userMapper.insert(user);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException("ACCOUNT_EXISTS", "Username, phone, or email is already registered");
        }
        return loginUser(user);
    }

    public AuthResponse login(LoginRequest request) {
        UserEntity user = findByAccount(request.account());
        if (user == null || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BusinessException("INVALID_CREDENTIALS", "Account or password is incorrect");
        }
        if (!Integer.valueOf(1).equals(user.getStatus())) {
            throw new BusinessException("ACCOUNT_DISABLED", "Account is disabled");
        }
        return loginUser(user);
    }

    public void logout(String token) {
        if (token != null) {
            redisTemplate.delete(RedisKeys.loginToken(token));
        }
    }

    public UserProfile currentUser(Long userId) {
        UserEntity user = userMapper.selectById(userId);
        if (user == null || !Integer.valueOf(1).equals(user.getStatus())) {
            throw new BusinessException("USER_NOT_FOUND", "Current user no longer exists or is disabled");
        }
        return toProfile(user);
    }

    private UserEntity findByAccount(String account) {
        return userMapper.selectOne(new LambdaQueryWrapper<UserEntity>()
                .eq(UserEntity::getUsername, account)
                .or(wrapper -> wrapper.eq(UserEntity::getPhone, account))
                .or(wrapper -> wrapper.eq(UserEntity::getEmail, account)));
    }

    private AuthResponse loginUser(UserEntity user) {
        String token = jwtTokenService.issue(user.getId(), user.getUsername(), user.getRole());
        redisTemplate.opsForValue().set(
                RedisKeys.loginToken(token),
                user.getId().toString(),
                jwtTokenService.tokenTtl());
        return new AuthResponse(token, toProfile(user));
    }

    private UserProfile toProfile(UserEntity user) {
        return new UserProfile(user.getId(), user.getUsername(), user.getPhone(), user.getEmail(), user.getRole());
    }
}
