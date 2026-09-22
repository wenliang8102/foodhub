package com.foodhub.coupon.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.foodhub.common.core.BusinessException;
import com.foodhub.coupon.client.MerchantClient;
import com.foodhub.coupon.dto.CouponCreateRequest;
import com.foodhub.coupon.entity.CouponEntity;
import com.foodhub.coupon.entity.UserCouponEntity;
import com.foodhub.coupon.mapper.CouponMapper;
import com.foodhub.coupon.mapper.UserCouponMapper;
import com.foodhub.coupon.vo.CouponView;
import com.foodhub.coupon.vo.PageResponse;
import com.foodhub.coupon.vo.UserCouponView;
import com.foodhub.coupon.web.CouponRequestIdentity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

@Service
public class CouponService {

    private static final ZoneId DATABASE_ZONE = ZoneId.of("Asia/Shanghai");
    private static final int MAX_PAGE_SIZE = 100;

    private final CouponMapper couponMapper;
    private final UserCouponMapper userCouponMapper;
    private final MerchantClient merchantClient;
    private final Clock clock;

    @Autowired
    public CouponService(CouponMapper couponMapper, UserCouponMapper userCouponMapper,
                         MerchantClient merchantClient) {
        this(couponMapper, userCouponMapper, merchantClient, Clock.system(DATABASE_ZONE));
    }

    CouponService(CouponMapper couponMapper, UserCouponMapper userCouponMapper,
                  MerchantClient merchantClient, Clock clock) {
        this.couponMapper = couponMapper;
        this.userCouponMapper = userCouponMapper;
        this.merchantClient = merchantClient;
        this.clock = clock;
    }

    public CouponView create(CouponRequestIdentity actor, CouponCreateRequest request) {
        actor.requireMerchantOrAdmin();
        LocalDateTime validFrom = toDatabaseTime(request.validFrom());
        LocalDateTime validUntil = toDatabaseTime(request.validUntil());
        LocalDateTime now = now();
        if (!validUntil.isAfter(validFrom)) {
            throw new BusinessException("COUPON_VALIDITY_INVALID", "validUntil must be after validFrom");
        }
        if (!validUntil.isAfter(now)) {
            throw new BusinessException("COUPON_VALIDITY_INVALID", "validUntil must be in the future");
        }
        if (request.minSpend().compareTo(request.faceValue()) < 0) {
            throw new BusinessException("COUPON_AMOUNT_INVALID", "minSpend must not be less than faceValue");
        }
        merchantClient.requireManageableMerchant(actor, request.merchantId());

        CouponEntity coupon = new CouponEntity();
        coupon.setMerchantId(request.merchantId());
        coupon.setCreatorUserId(actor.userId());
        coupon.setName(request.name().trim());
        coupon.setFaceValue(request.faceValue());
        coupon.setMinSpend(request.minSpend());
        coupon.setTotalStock(request.totalStock());
        coupon.setRemainingStock(request.totalStock());
        coupon.setValidFrom(validFrom);
        coupon.setValidUntil(validUntil);
        coupon.setStatus("ACTIVE");
        coupon.setCreatedAt(now);
        coupon.setUpdatedAt(now);
        couponMapper.insert(coupon);
        return toCouponView(coupon, now);
    }

    @Transactional(readOnly = true)
    public PageResponse<CouponView> listAvailable(Long merchantId, int page, int size) {
        validatePagination(page, size);
        if (merchantId != null && merchantId <= 0) {
            throw new BusinessException("VALIDATION_ERROR", "merchantId must be positive");
        }
        LocalDateTime now = now();
        LambdaQueryWrapper<CouponEntity> query = new LambdaQueryWrapper<CouponEntity>()
                .eq(CouponEntity::getStatus, "ACTIVE")
                .le(CouponEntity::getValidFrom, now)
                .gt(CouponEntity::getValidUntil, now)
                .gt(CouponEntity::getRemainingStock, 0)
                .eq(merchantId != null, CouponEntity::getMerchantId, merchantId)
                .orderByDesc(CouponEntity::getCreatedAt)
                .orderByDesc(CouponEntity::getId);
        Page<CouponEntity> result = couponMapper.selectPage(Page.of(page, size), query);
        return page(result, page, size, now);
    }

    @Transactional(readOnly = true)
    public CouponView detail(long couponId) {
        CouponEntity coupon = requireCoupon(couponId);
        if (!"ACTIVE".equals(coupon.getStatus())) {
            throw new BusinessException("COUPON_NOT_FOUND", "Coupon does not exist or is unavailable");
        }
        return toCouponView(coupon, now());
    }

    @Transactional(readOnly = true)
    public PageResponse<CouponView> managed(CouponRequestIdentity actor, int page, int size) {
        actor.requireMerchantOrAdmin();
        validatePagination(page, size);
        LambdaQueryWrapper<CouponEntity> query = new LambdaQueryWrapper<CouponEntity>()
                .eq(!actor.isAdmin(), CouponEntity::getCreatorUserId, actor.userId())
                .orderByDesc(CouponEntity::getCreatedAt)
                .orderByDesc(CouponEntity::getId);
        Page<CouponEntity> result = couponMapper.selectPage(Page.of(page, size), query);
        return page(result, page, size, now());
    }

    @Transactional
    public CouponView updateStatus(CouponRequestIdentity actor, long couponId, String status) {
        actor.requireMerchantOrAdmin();
        CouponEntity coupon = requireCoupon(couponId);
        requireOwnerOrAdmin(actor, coupon);
        String normalizedStatus = status.trim().toUpperCase();
        if (!"ACTIVE".equals(normalizedStatus) && !"INACTIVE".equals(normalizedStatus)) {
            throw new BusinessException("COUPON_STATUS_INVALID", "status must be ACTIVE or INACTIVE");
        }
        coupon.setStatus(normalizedStatus);
        coupon.setUpdatedAt(now());
        couponMapper.updateById(coupon);
        return toCouponView(coupon, now());
    }

    @Transactional
    public UserCouponView claim(long userId, long couponId) {
        if (userId <= 0) {
            throw new BusinessException("UNAUTHORIZED", "X-User-Id header must be a positive number");
        }
        boolean alreadyClaimed = userCouponMapper.selectCount(
                new LambdaQueryWrapper<UserCouponEntity>()
                        .eq(UserCouponEntity::getUserId, userId)
                        .eq(UserCouponEntity::getCouponId, couponId)) > 0;
        if (alreadyClaimed) {
            throw new BusinessException("COUPON_ALREADY_CLAIMED", "Coupon has already been claimed");
        }

        LocalDateTime now = now();
        if (couponMapper.decrementStockIfClaimable(couponId, now) != 1) {
            throw claimFailure(couponId, now);
        }
        UserCouponEntity userCoupon = new UserCouponEntity();
        userCoupon.setUserId(userId);
        userCoupon.setCouponId(couponId);
        userCoupon.setStatus("UNUSED");
        userCoupon.setClaimedAt(now);
        userCoupon.setCreatedAt(now);
        userCoupon.setUpdatedAt(now);
        try {
            userCouponMapper.insert(userCoupon);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException("COUPON_ALREADY_CLAIMED", "Coupon has already been claimed");
        }
        CouponEntity coupon = requireCoupon(couponId);
        return toUserCouponView(userCoupon, coupon, now);
    }

    @Transactional(readOnly = true)
    public PageResponse<UserCouponView> mine(long userId, int page, int size) {
        validatePagination(page, size);
        Page<UserCouponEntity> result = userCouponMapper.selectPage(
                Page.of(page, size),
                new LambdaQueryWrapper<UserCouponEntity>()
                        .eq(UserCouponEntity::getUserId, userId)
                        .orderByDesc(UserCouponEntity::getClaimedAt)
                        .orderByDesc(UserCouponEntity::getId));
        LocalDateTime now = now();
        List<UserCouponView> items = result.getRecords().stream()
                .map(item -> toUserCouponView(item, requireCoupon(item.getCouponId()), now))
                .toList();
        return new PageResponse<>(items, page, size, result.getTotal());
    }

    private BusinessException claimFailure(long couponId, LocalDateTime now) {
        CouponEntity coupon = couponMapper.selectById(couponId);
        if (coupon == null || !"ACTIVE".equals(coupon.getStatus())) {
            return new BusinessException("COUPON_NOT_FOUND", "Coupon does not exist or is unavailable");
        }
        if (coupon.getValidFrom().isAfter(now)) {
            return new BusinessException("COUPON_NOT_STARTED", "Coupon claim period has not started");
        }
        if (!coupon.getValidUntil().isAfter(now)) {
            return new BusinessException("COUPON_EXPIRED", "Coupon has expired");
        }
        return new BusinessException("COUPON_SOLD_OUT", "Coupon stock is exhausted");
    }

    private CouponEntity requireCoupon(long couponId) {
        if (couponId <= 0) {
            throw new BusinessException("COUPON_NOT_FOUND", "Coupon does not exist");
        }
        CouponEntity coupon = couponMapper.selectById(couponId);
        if (coupon == null) {
            throw new BusinessException("COUPON_NOT_FOUND", "Coupon does not exist");
        }
        return coupon;
    }

    private void requireOwnerOrAdmin(CouponRequestIdentity actor, CouponEntity coupon) {
        if (!actor.isAdmin() && !coupon.getCreatorUserId().equals(actor.userId())) {
            throw new BusinessException("COUPON_FORBIDDEN", "Merchant can only manage its own coupons");
        }
    }

    private PageResponse<CouponView> page(Page<CouponEntity> result, int page, int size,
                                          LocalDateTime now) {
        return new PageResponse<>(result.getRecords().stream()
                .map(coupon -> toCouponView(coupon, now))
                .toList(), page, size, result.getTotal());
    }

    private CouponView toCouponView(CouponEntity coupon, LocalDateTime now) {
        String displayStatus = displayStatus(coupon, now);
        return new CouponView(
                coupon.getId(), coupon.getMerchantId(), coupon.getName(), coupon.getFaceValue(),
                coupon.getMinSpend(), coupon.getTotalStock(), coupon.getRemainingStock(),
                toOffset(coupon.getValidFrom()), toOffset(coupon.getValidUntil()), displayStatus,
                "AVAILABLE".equals(displayStatus));
    }

    private UserCouponView toUserCouponView(UserCouponEntity userCoupon, CouponEntity coupon,
                                            LocalDateTime now) {
        String status = userCoupon.getStatus();
        if ("UNUSED".equals(status) && !coupon.getValidUntil().isAfter(now)) {
            status = "EXPIRED";
        }
        return new UserCouponView(
                userCoupon.getId(), status, toOffset(userCoupon.getClaimedAt()),
                toCouponView(coupon, now));
    }

    private String displayStatus(CouponEntity coupon, LocalDateTime now) {
        if (!"ACTIVE".equals(coupon.getStatus())) {
            return "INACTIVE";
        }
        if (coupon.getValidFrom().isAfter(now)) {
            return "NOT_STARTED";
        }
        if (!coupon.getValidUntil().isAfter(now)) {
            return "EXPIRED";
        }
        return coupon.getRemainingStock() > 0 ? "AVAILABLE" : "SOLD_OUT";
    }

    private void validatePagination(int page, int size) {
        if (page < 1 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new BusinessException("VALIDATION_ERROR", "page must be at least 1 and size must be 1 to 100");
        }
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    private LocalDateTime toDatabaseTime(OffsetDateTime value) {
        return value.atZoneSameInstant(DATABASE_ZONE).toLocalDateTime();
    }

    private OffsetDateTime toOffset(LocalDateTime value) {
        return value.atZone(DATABASE_ZONE).toOffsetDateTime();
    }
}
