package com.foodhub.common.redis;

public final class RedisKeys {

    public static final String PREFIX = "foodhub:";

    private RedisKeys() {
    }

    public static String loginToken(String token) {
        return PREFIX + "login:token:" + token;
    }

    public static String merchantDetail(long merchantId) {
        return PREFIX + "merchant:detail:" + merchantId;
    }

    public static String foodDetail(long foodId) {
        return PREFIX + "food:detail:" + foodId;
    }

    public static String seckillStock(long activityId) {
        return PREFIX + "seckill:stock:" + activityId;
    }

    public static String seckillUsers(long activityId) {
        return PREFIX + "seckill:users:" + activityId;
    }

    public static String seckillPath(long activityId, long userId) {
        return PREFIX + "seckill:path:" + activityId + ":" + userId;
    }

    public static String seckillRequest(long activityId, long userId) {
        return PREFIX + "seckill:request:" + activityId + ":" + userId;
    }
}
