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
}
