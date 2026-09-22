package com.foodhub.coupon.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.foodhub.common.core.BusinessException;
import com.foodhub.common.messaging.SeckillOrderCreateCommand;
import com.foodhub.common.messaging.SeckillTargetType;
import com.foodhub.coupon.client.MerchantClient;
import com.foodhub.coupon.dto.SeckillActivityCreateRequest;
import com.foodhub.coupon.entity.CouponEntity;
import com.foodhub.coupon.entity.SeckillActivityEntity;
import com.foodhub.coupon.entity.SeckillRequestEntity;
import com.foodhub.coupon.mapper.CouponMapper;
import com.foodhub.coupon.mapper.SeckillActivityMapper;
import com.foodhub.coupon.mapper.SeckillRequestMapper;
import com.foodhub.coupon.messaging.SeckillOrderCommandPublisher;
import com.foodhub.coupon.vo.PageResponse;
import com.foodhub.coupon.vo.SeckillActivityView;
import com.foodhub.coupon.vo.SeckillPathView;
import com.foodhub.coupon.vo.SeckillResultView;
import com.foodhub.coupon.web.CouponRequestIdentity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.UUID;

@Service
public class SeckillService {

    private static final ZoneId DATABASE_ZONE = ZoneId.of("Asia/Shanghai");
    private static final Duration PATH_TTL = Duration.ofMinutes(5);
    private static final Duration PAYMENT_TTL = Duration.ofMinutes(15);
    private static final int MAX_PAGE_SIZE = 100;

    private final SeckillActivityMapper activityMapper;
    private final SeckillRequestMapper requestMapper;
    private final CouponMapper couponMapper;
    private final MerchantClient merchantClient;
    private final SeckillRedisService redisService;
    private final SeckillReservationPersistence reservationPersistence;
    private final SeckillOrderCommandPublisher commandPublisher;
    private final Clock clock;

    @Autowired
    public SeckillService(SeckillActivityMapper activityMapper,
                          SeckillRequestMapper requestMapper,
                          CouponMapper couponMapper,
                          MerchantClient merchantClient,
                          SeckillRedisService redisService,
                          SeckillReservationPersistence reservationPersistence,
                          SeckillOrderCommandPublisher commandPublisher) {
        this(activityMapper, requestMapper, couponMapper, merchantClient, redisService,
                reservationPersistence, commandPublisher, Clock.system(DATABASE_ZONE));
    }

    SeckillService(SeckillActivityMapper activityMapper,
                   SeckillRequestMapper requestMapper,
                   CouponMapper couponMapper,
                   MerchantClient merchantClient,
                   SeckillRedisService redisService,
                   SeckillReservationPersistence reservationPersistence,
                   SeckillOrderCommandPublisher commandPublisher,
                   Clock clock) {
        this.activityMapper = activityMapper;
        this.requestMapper = requestMapper;
        this.couponMapper = couponMapper;
        this.merchantClient = merchantClient;
        this.redisService = redisService;
        this.reservationPersistence = reservationPersistence;
        this.commandPublisher = commandPublisher;
        this.clock = clock;
    }

    public SeckillActivityView create(CouponRequestIdentity actor,
                                      SeckillActivityCreateRequest request) {
        actor.requireMerchantOrAdmin();
        LocalDateTime startAt = databaseTime(request.startAt());
        LocalDateTime endAt = databaseTime(request.endAt());
        LocalDateTime now = now();
        if (!endAt.isAfter(startAt) || !endAt.isAfter(now)) {
            throw new BusinessException(
                    "SECKILL_TIME_INVALID", "endAt must be after startAt and in the future");
        }
        merchantClient.requireManageableMerchant(actor, request.merchantId());
        validateTarget(request.targetType(), request.targetId(), request.merchantId());

        SeckillActivityEntity activity = new SeckillActivityEntity();
        activity.setMerchantId(request.merchantId());
        activity.setCreatorUserId(actor.userId());
        activity.setTargetType(request.targetType());
        activity.setTargetId(request.targetId());
        activity.setTitle(request.title().trim());
        activity.setSeckillPrice(request.seckillPrice());
        activity.setTotalStock(request.totalStock());
        activity.setRemainingStock(request.totalStock());
        activity.setStartAt(startAt);
        activity.setEndAt(endAt);
        activity.setStatus("ACTIVE");
        activity.setVersion(0);
        activity.setCreatedAt(now);
        activity.setUpdatedAt(now);
        activityMapper.insert(activity);
        return view(activity, now);
    }

    @Transactional(readOnly = true)
    public PageResponse<SeckillActivityView> list(int page, int size) {
        validatePagination(page, size);
        Page<SeckillActivityEntity> result = activityMapper.selectPage(
                Page.of(page, size),
                new LambdaQueryWrapper<SeckillActivityEntity>()
                        .eq(SeckillActivityEntity::getStatus, "ACTIVE")
                        .gt(SeckillActivityEntity::getEndAt, now())
                        .orderByAsc(SeckillActivityEntity::getStartAt)
                        .orderByDesc(SeckillActivityEntity::getId));
        LocalDateTime now = now();
        return new PageResponse<>(result.getRecords().stream()
                .map(activity -> view(activity, now)).toList(), page, size, result.getTotal());
    }

    @Transactional(readOnly = true)
    public SeckillActivityView detail(long activityId) {
        SeckillActivityEntity activity = requireActivity(activityId);
        if (!"ACTIVE".equals(activity.getStatus())) {
            throw new BusinessException("SECKILL_NOT_FOUND", "Seckill activity is unavailable");
        }
        return view(activity, now());
    }

    public SeckillActivityView preheat(CouponRequestIdentity actor, long activityId) {
        actor.requireMerchantOrAdmin();
        SeckillActivityEntity activity = requireActivity(activityId);
        requireOwnerOrAdmin(actor, activity);
        LocalDateTime now = now();
        if (!"ACTIVE".equals(activity.getStatus()) || !now.isBefore(activity.getStartAt())) {
            throw new BusinessException(
                    "SECKILL_PREHEAT_FORBIDDEN", "Stock can only be preheated before an active activity starts");
        }
        Duration ttl = Duration.between(now, activity.getEndAt()).plusDays(1);
        redisService.preheat(activityId, activity.getRemainingStock(), ttl);
        return view(activity, now);
    }

    public SeckillPathView issuePath(long userId, long activityId) {
        SeckillActivityEntity activity = requireActiveNow(activityId);
        if (!redisService.isReady(activityId)) {
            throw new BusinessException("SECKILL_NOT_READY", "Seckill stock has not been preheated");
        }
        String existingRequest = redisService.requestId(activityId, userId);
        if (existingRequest != null) {
            throw new BusinessException("SECKILL_DUPLICATE", "User has already joined this activity");
        }
        String path = UUID.randomUUID().toString().replace("-", "");
        Duration ttl = min(PATH_TTL, Duration.between(now(), activity.getEndAt()));
        redisService.savePath(activityId, userId, path, ttl);
        return new SeckillPathView(path, OffsetDateTime.now(clock).plus(ttl));
    }

    public SeckillResultView submit(long userId, long activityId, String path) {
        SeckillActivityEntity activity = requireActiveNow(activityId);
        String requestId = "SK" + UUID.randomUUID().toString().replace("-", "");
        Duration ttl = Duration.between(now(), activity.getEndAt()).plusDays(1);
        long result = redisService.reserve(activityId, userId, path, requestId, ttl);
        if (result != 0) {
            throw reserveFailure(result);
        }
        try {
            SeckillOrderCreateCommand command = new SeckillOrderCreateCommand(
                    "MSG" + UUID.randomUUID().toString().replace("-", ""),
                    requestId,
                    userId,
                    activityId,
                    SeckillTargetType.valueOf(activity.getTargetType()),
                    activity.getTargetId(),
                    1,
                    activity.getSeckillPrice(),
                    clock.instant(),
                    clock.instant().plus(PAYMENT_TTL));
            SeckillRequestEntity request = reservationPersistence.reserve(command, now());
            if (commandPublisher.publishRequest(requestId)) {
                request.setStatus("MESSAGE_SENT");
            }
            return resultView(request);
        } catch (RuntimeException exception) {
            redisService.rollback(activityId, userId, requestId);
            throw exception;
        }
    }

    @Transactional(readOnly = true)
    public SeckillResultView result(long userId, long activityId) {
        SeckillRequestEntity request = requestMapper.selectOne(
                new LambdaQueryWrapper<SeckillRequestEntity>()
                        .eq(SeckillRequestEntity::getActivityId, activityId)
                        .eq(SeckillRequestEntity::getUserId, userId));
        if (request == null) {
            throw new BusinessException("SECKILL_RESULT_NOT_FOUND", "No seckill result was found");
        }
        return resultView(request);
    }

    private void validateTarget(String targetType, long targetId, long merchantId) {
        if ("FOOD_ITEM".equals(targetType)) {
            merchantClient.requireFoodBelongsToMerchant(targetId, merchantId);
            return;
        }
        CouponEntity coupon = couponMapper.selectById(targetId);
        if (coupon == null || !coupon.getMerchantId().equals(merchantId)) {
            throw new BusinessException(
                    "SECKILL_TARGET_INVALID", "Coupon does not belong to the selected merchant");
        }
    }

    private SeckillActivityEntity requireActiveNow(long activityId) {
        SeckillActivityEntity activity = requireActivity(activityId);
        LocalDateTime now = now();
        if (!"ACTIVE".equals(activity.getStatus())) {
            throw new BusinessException("SECKILL_NOT_FOUND", "Seckill activity is unavailable");
        }
        if (now.isBefore(activity.getStartAt())) {
            throw new BusinessException("SECKILL_NOT_STARTED", "Seckill activity has not started");
        }
        if (!now.isBefore(activity.getEndAt())) {
            throw new BusinessException("SECKILL_ENDED", "Seckill activity has ended");
        }
        return activity;
    }

    private SeckillActivityEntity requireActivity(long activityId) {
        SeckillActivityEntity activity = activityId > 0 ? activityMapper.selectById(activityId) : null;
        if (activity == null) {
            throw new BusinessException("SECKILL_NOT_FOUND", "Seckill activity does not exist");
        }
        return activity;
    }

    private BusinessException reserveFailure(long result) {
        return switch ((int) result) {
            case 1 -> new BusinessException("SECKILL_SOLD_OUT", "Seckill stock is exhausted");
            case 2 -> new BusinessException("SECKILL_DUPLICATE", "User has already joined this activity");
            case 4 -> new BusinessException("SECKILL_PATH_INVALID", "Seckill path is invalid or expired");
            default -> new BusinessException("SECKILL_NOT_READY", "Seckill stock has not been preheated");
        };
    }

    private SeckillActivityView view(SeckillActivityEntity activity, LocalDateTime now) {
        Integer redisStock = redisService.stock(activity.getId());
        String status;
        if (!"ACTIVE".equals(activity.getStatus())) status = "INACTIVE";
        else if (now.isBefore(activity.getStartAt())) status = "NOT_STARTED";
        else if (!now.isBefore(activity.getEndAt())) status = "ENDED";
        else if ((redisStock != null ? redisStock : activity.getRemainingStock()) <= 0) status = "SOLD_OUT";
        else status = "ACTIVE";
        return new SeckillActivityView(
                activity.getId(), activity.getMerchantId(), activity.getTargetType(), activity.getTargetId(),
                activity.getTitle(), activity.getSeckillPrice(), activity.getTotalStock(),
                redisStock != null ? redisStock : activity.getRemainingStock(),
                offset(activity.getStartAt()), offset(activity.getEndAt()), status, redisStock != null);
    }

    private SeckillResultView resultView(SeckillRequestEntity request) {
        return new SeckillResultView(request.getRequestId(), request.getActivityId(), request.getStatus(),
                request.getOrderNo(), request.getFailureCode(), offset(request.getCreatedAt()));
    }

    private void requireOwnerOrAdmin(CouponRequestIdentity actor, SeckillActivityEntity activity) {
        if (!actor.isAdmin() && !activity.getCreatorUserId().equals(actor.userId())) {
            throw new BusinessException("SECKILL_FORBIDDEN", "Merchant can only manage its own activity");
        }
    }

    private void validatePagination(int page, int size) {
        if (page < 1 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new BusinessException("VALIDATION_ERROR", "page must be at least 1 and size must be 1 to 100");
        }
    }

    private Duration min(Duration left, Duration right) {
        return left.compareTo(right) <= 0 ? left : right;
    }

    private LocalDateTime now() { return LocalDateTime.now(clock); }
    private LocalDateTime databaseTime(OffsetDateTime value) {
        return value.atZoneSameInstant(DATABASE_ZONE).toLocalDateTime();
    }
    private OffsetDateTime offset(LocalDateTime value) {
        return value.atZone(DATABASE_ZONE).toOffsetDateTime();
    }
}
