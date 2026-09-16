package com.foodhub.merchant.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodhub.common.core.BusinessException;
import com.foodhub.common.redis.RedisKeys;
import com.foodhub.merchant.dto.BusinessStatus;
import com.foodhub.merchant.dto.FoodCreateRequest;
import com.foodhub.merchant.dto.FoodUpdateRequest;
import com.foodhub.merchant.dto.MerchantCreateRequest;
import com.foodhub.merchant.dto.MerchantUpdateRequest;
import com.foodhub.merchant.dto.ResourceStatus;
import com.foodhub.merchant.entity.CategoryEntity;
import com.foodhub.merchant.entity.FoodItemEntity;
import com.foodhub.merchant.entity.MerchantEntity;
import com.foodhub.merchant.mapper.CategoryMapper;
import com.foodhub.merchant.mapper.FoodItemMapper;
import com.foodhub.merchant.mapper.MerchantMapper;
import com.foodhub.merchant.vo.CategoryView;
import com.foodhub.merchant.vo.FoodItemView;
import com.foodhub.merchant.vo.MerchantView;
import com.foodhub.merchant.vo.PageResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
@Profile("!mock")
public class DbMerchantCatalogService implements MerchantCatalogService {

    private static final Logger log = LoggerFactory.getLogger(DbMerchantCatalogService.class);
    private static final int MAX_PAGE_SIZE = 100;
    private static final Duration DETAIL_CACHE_TTL = Duration.ofMinutes(30);

    private final CategoryMapper categoryMapper;
    private final MerchantMapper merchantMapper;
    private final FoodItemMapper foodItemMapper;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public DbMerchantCatalogService(
            CategoryMapper categoryMapper,
            MerchantMapper merchantMapper,
            FoodItemMapper foodItemMapper,
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper) {
        this.categoryMapper = categoryMapper;
        this.merchantMapper = merchantMapper;
        this.foodItemMapper = foodItemMapper;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.clock = Clock.systemUTC();
    }

    @Override
    @Transactional(readOnly = true)
    public List<CategoryView> listCategories() {
        return categoryMapper.selectList(new LambdaQueryWrapper<CategoryEntity>()
                        .eq(CategoryEntity::getStatus, ResourceStatus.ACTIVE.name())
                        .orderByAsc(CategoryEntity::getSortOrder)
                        .orderByAsc(CategoryEntity::getId))
                .stream()
                .map(this::toCategoryView)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<MerchantView> listMerchants(
            String keyword,
            Long categoryId,
            String sort,
            int page,
            int size) {
        ensureCategoryExistsIfPresent(categoryId);
        List<MerchantView> items = merchantMapper.selectList(new LambdaQueryWrapper<MerchantEntity>()
                        .eq(MerchantEntity::getStatus, ResourceStatus.ACTIVE.name()))
                .stream()
                .filter(merchant -> categoryId == null || merchant.getCategoryId().equals(categoryId))
                .filter(merchant -> matches(keyword, merchant.getName(), merchant.getAddress()))
                .sorted(merchantComparator(sort))
                .map(this::toMerchantView)
                .toList();
        return page(items, page, size);
    }

    @Override
    @Transactional(readOnly = true)
    public MerchantView getMerchant(Long merchantId) {
        String cacheKey = RedisKeys.merchantDetail(merchantId);
        Optional<MerchantView> cached = readCache(cacheKey, MerchantView.class);
        if (cached.isPresent()) {
            return cached.get();
        }
        MerchantEntity merchant = findMerchant(merchantId);
        if (!isPublicMerchant(merchant)) {
            throw new BusinessException("MERCHANT_NOT_FOUND", "Merchant does not exist or is unavailable");
        }
        MerchantView view = toMerchantView(merchant);
        writeCache(cacheKey, view);
        return view;
    }

    @Override
    @Transactional
    public MerchantView createMerchant(
            String rawUserId,
            String rawRole,
            MerchantCreateRequest request) {
        Actor actor = requireWriter(rawUserId, rawRole);
        CategoryEntity category = findActiveCategory(request.categoryId());
        LocalDateTime now = now();

        MerchantEntity merchant = new MerchantEntity();
        merchant.setOwnerUserId(actor.userId());
        merchant.setCategoryId(category.getId());
        merchant.setName(request.name().trim());
        merchant.setAddress(request.address().trim());
        merchant.setLongitude(request.longitude());
        merchant.setLatitude(request.latitude());
        merchant.setRating(new BigDecimal("5.00"));
        merchant.setSalesCount(0L);
        merchant.setBusinessStatus(BusinessStatus.CLOSED.name());
        merchant.setStatus(ResourceStatus.ACTIVE.name());
        merchant.setCreatedAt(now);
        merchant.setUpdatedAt(now);
        merchantMapper.insert(merchant);
        return toMerchantView(merchant);
    }

    @Override
    @Transactional
    public MerchantView updateMerchant(
            String rawUserId,
            String rawRole,
            Long merchantId,
            MerchantUpdateRequest request) {
        Actor actor = requireWriter(rawUserId, rawRole);
        MerchantEntity merchant = findMerchant(merchantId);
        requireOwnerOrAdmin(actor, merchant.getOwnerUserId());
        if (request.categoryId() != null) {
            findActiveCategory(request.categoryId());
            merchant.setCategoryId(request.categoryId());
        }
        if (StringUtils.hasText(request.name())) {
            merchant.setName(request.name().trim());
        }
        if (StringUtils.hasText(request.address())) {
            merchant.setAddress(request.address().trim());
        }
        if (request.longitude() != null) {
            merchant.setLongitude(request.longitude());
        }
        if (request.latitude() != null) {
            merchant.setLatitude(request.latitude());
        }
        if (request.businessStatus() != null) {
            merchant.setBusinessStatus(request.businessStatus().name());
        }
        if (request.status() != null) {
            merchant.setStatus(request.status().name());
        }
        merchant.setUpdatedAt(now());
        merchantMapper.updateById(merchant);
        evictMerchantCaches(merchant.getId());
        return toMerchantView(merchant);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<FoodItemView> listFoods(
            Long merchantId,
            Long categoryId,
            String keyword,
            String sort,
            int page,
            int size) {
        ensureMerchantExistsIfPresent(merchantId);
        ensureCategoryExistsIfPresent(categoryId);
        List<FoodItemView> items = foodItemMapper.selectList(new LambdaQueryWrapper<FoodItemEntity>()
                        .eq(FoodItemEntity::getStatus, ResourceStatus.ACTIVE.name())
                        .eq(FoodItemEntity::getOnSale, true))
                .stream()
                .filter(this::isPublicFood)
                .filter(food -> merchantId == null || food.getMerchantId().equals(merchantId))
                .filter(food -> categoryId == null || findMerchant(food.getMerchantId()).getCategoryId().equals(categoryId))
                .filter(food -> matches(keyword, food.getName(), findMerchant(food.getMerchantId()).getName()))
                .sorted(foodComparator(sort))
                .map(this::toFoodItemView)
                .toList();
        return page(items, page, size);
    }

    @Override
    @Transactional(readOnly = true)
    public FoodItemView getFood(Long foodId) {
        String cacheKey = RedisKeys.foodDetail(foodId);
        Optional<FoodItemView> cached = readCache(cacheKey, FoodItemView.class);
        if (cached.isPresent()) {
            return cached.get();
        }
        FoodItemEntity food = findFood(foodId);
        if (!isPublicFood(food)) {
            throw new BusinessException("FOOD_NOT_FOUND", "Food item does not exist or is unavailable");
        }
        FoodItemView view = toFoodItemView(food);
        writeCache(cacheKey, view);
        return view;
    }

    @Override
    @Transactional
    public FoodItemView createFood(
            String rawUserId,
            String rawRole,
            FoodCreateRequest request) {
        Actor actor = requireWriter(rawUserId, rawRole);
        MerchantEntity merchant = findMerchant(request.merchantId());
        requireOwnerOrAdmin(actor, merchant.getOwnerUserId());
        if (!isPublicMerchant(merchant)) {
            throw new BusinessException("MERCHANT_UNAVAILABLE", "Food cannot be added to an inactive merchant");
        }
        LocalDateTime now = now();

        FoodItemEntity food = new FoodItemEntity();
        food.setMerchantId(merchant.getId());
        food.setName(request.name().trim());
        food.setPrice(request.price());
        food.setImageUrl(trimToNull(request.imageUrl()));
        food.setSalesCount(0L);
        food.setOnSale(true);
        food.setStatus(ResourceStatus.ACTIVE.name());
        food.setCreatedAt(now);
        food.setUpdatedAt(now);
        foodItemMapper.insert(food);
        return toFoodItemView(food);
    }

    @Override
    @Transactional
    public FoodItemView updateFood(
            String rawUserId,
            String rawRole,
            Long foodId,
            FoodUpdateRequest request) {
        Actor actor = requireWriter(rawUserId, rawRole);
        FoodItemEntity food = findFood(foodId);
        MerchantEntity merchant = findMerchant(food.getMerchantId());
        requireOwnerOrAdmin(actor, merchant.getOwnerUserId());
        if (StringUtils.hasText(request.name())) {
            food.setName(request.name().trim());
        }
        if (request.price() != null) {
            food.setPrice(request.price());
        }
        if (request.imageUrl() != null) {
            food.setImageUrl(trimToNull(request.imageUrl()));
        }
        if (request.onSale() != null) {
            food.setOnSale(request.onSale());
        }
        if (request.status() != null) {
            food.setStatus(request.status().name());
        }
        food.setUpdatedAt(now());
        foodItemMapper.updateById(food);
        evictCache(RedisKeys.foodDetail(food.getId()));
        return toFoodItemView(food);
    }

    private <T> Optional<T> readCache(String key, Class<T> type) {
        try {
            String json = redisTemplate.opsForValue().get(key);
            if (!StringUtils.hasText(json)) {
                return Optional.empty();
            }
            return Optional.of(objectMapper.readValue(json, type));
        } catch (JsonProcessingException exception) {
            log.debug("Invalid cached JSON for key {}", key, exception);
            evictCache(key);
            return Optional.empty();
        } catch (RuntimeException exception) {
            log.debug("Redis read failed for key {}", key, exception);
            return Optional.empty();
        }
    }

    private void writeCache(String key, Object value) {
        try {
            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(value), DETAIL_CACHE_TTL);
        } catch (JsonProcessingException exception) {
            log.debug("JSON serialization failed for key {}", key, exception);
        } catch (RuntimeException exception) {
            log.debug("Redis write failed for key {}", key, exception);
        }
    }

    private void evictMerchantCaches(Long merchantId) {
        evictCache(RedisKeys.merchantDetail(merchantId));
        foodItemMapper.selectList(new LambdaQueryWrapper<FoodItemEntity>()
                        .eq(FoodItemEntity::getMerchantId, merchantId))
                .forEach(food -> evictCache(RedisKeys.foodDetail(food.getId())));
    }

    private void evictCache(String key) {
        try {
            redisTemplate.delete(key);
        } catch (RuntimeException exception) {
            log.debug("Redis delete failed for key {}", key, exception);
        }
    }

    private <T> PageResponse<T> page(List<T> items, int rawPage, int rawSize) {
        List<T> pageItems = new ArrayList<>(items);
        int page = Math.max(rawPage, 1);
        int size = Math.min(Math.max(rawSize, 1), MAX_PAGE_SIZE);
        int from = Math.min((page - 1) * size, pageItems.size());
        int to = Math.min(from + size, pageItems.size());
        return new PageResponse<>(pageItems.subList(from, to), page, size, pageItems.size());
    }

    private Comparator<MerchantEntity> merchantComparator(String sort) {
        if ("sales".equalsIgnoreCase(sort)) {
            return Comparator.comparingLong((MerchantEntity merchant) -> merchant.getSalesCount()).reversed()
                    .thenComparing(MerchantEntity::getId);
        }
        return Comparator.comparing((MerchantEntity merchant) -> merchant.getRating()).reversed()
                .thenComparing(MerchantEntity::getId);
    }

    private Comparator<FoodItemEntity> foodComparator(String sort) {
        if ("price".equalsIgnoreCase(sort)) {
            return Comparator.comparing(FoodItemEntity::getPrice);
        }
        return Comparator.comparingLong((FoodItemEntity food) -> food.getSalesCount()).reversed()
                .thenComparing(FoodItemEntity::getId);
    }

    private boolean matches(String keyword, String... values) {
        if (!StringUtils.hasText(keyword)) {
            return true;
        }
        String normalized = keyword.trim().toLowerCase(Locale.ROOT);
        for (String value : values) {
            if (value != null && value.toLowerCase(Locale.ROOT).contains(normalized)) {
                return true;
            }
        }
        return false;
    }

    private boolean isPublicMerchant(MerchantEntity merchant) {
        return ResourceStatus.ACTIVE.name().equals(merchant.getStatus());
    }

    private boolean isPublicFood(FoodItemEntity food) {
        MerchantEntity merchant = findMerchant(food.getMerchantId());
        return ResourceStatus.ACTIVE.name().equals(food.getStatus())
                && Boolean.TRUE.equals(food.getOnSale())
                && isPublicMerchant(merchant);
    }

    private CategoryEntity findActiveCategory(Long categoryId) {
        CategoryEntity category = categoryMapper.selectById(categoryId);
        if (category == null || !ResourceStatus.ACTIVE.name().equals(category.getStatus())) {
            throw new BusinessException("CATEGORY_NOT_FOUND", "Category does not exist or is inactive");
        }
        return category;
    }

    private MerchantEntity findMerchant(Long merchantId) {
        MerchantEntity merchant = merchantMapper.selectById(merchantId);
        if (merchant == null) {
            throw new BusinessException("MERCHANT_NOT_FOUND", "Merchant does not exist");
        }
        return merchant;
    }

    private FoodItemEntity findFood(Long foodId) {
        FoodItemEntity food = foodItemMapper.selectById(foodId);
        if (food == null) {
            throw new BusinessException("FOOD_NOT_FOUND", "Food item does not exist");
        }
        return food;
    }

    private void ensureCategoryExistsIfPresent(Long categoryId) {
        if (categoryId != null) {
            findActiveCategory(categoryId);
        }
    }

    private void ensureMerchantExistsIfPresent(Long merchantId) {
        if (merchantId != null) {
            findMerchant(merchantId);
        }
    }

    private Actor requireWriter(String rawUserId, String rawRole) {
        Long userId = parseUserId(rawUserId);
        String role = rawRole == null ? "" : rawRole.trim().toUpperCase(Locale.ROOT);
        if (!"MERCHANT".equals(role) && !"ADMIN".equals(role)) {
            throw new BusinessException("FORBIDDEN", "MERCHANT or ADMIN role is required");
        }
        return new Actor(userId, role);
    }

    private Long parseUserId(String rawUserId) {
        if (!StringUtils.hasText(rawUserId)) {
            throw new BusinessException("UNAUTHORIZED", "X-User-Id header is required");
        }
        try {
            long value = Long.parseLong(rawUserId.trim());
            if (value <= 0) {
                throw new NumberFormatException("non-positive");
            }
            return value;
        } catch (NumberFormatException exception) {
            throw new BusinessException("UNAUTHORIZED", "X-User-Id header must be a positive number");
        }
    }

    private void requireOwnerOrAdmin(Actor actor, Long ownerUserId) {
        if (!actor.isAdmin() && !actor.userId().equals(ownerUserId)) {
            throw new BusinessException("FORBIDDEN", "Merchant role can only manage its own merchant data");
        }
    }

    private MerchantView toMerchantView(MerchantEntity merchant) {
        CategoryEntity category = categoryMapper.selectById(merchant.getCategoryId());
        return new MerchantView(
                merchant.getId(),
                merchant.getOwnerUserId(),
                merchant.getCategoryId(),
                category == null ? null : category.getName(),
                merchant.getName(),
                merchant.getAddress(),
                merchant.getLongitude(),
                merchant.getLatitude(),
                merchant.getRating(),
                merchant.getSalesCount(),
                BusinessStatus.valueOf(merchant.getBusinessStatus()),
                ResourceStatus.valueOf(merchant.getStatus()),
                toOffsetDateTime(merchant.getCreatedAt()),
                toOffsetDateTime(merchant.getUpdatedAt()));
    }

    private CategoryView toCategoryView(CategoryEntity category) {
        return new CategoryView(category.getId(), category.getName(), category.getSortOrder());
    }

    private FoodItemView toFoodItemView(FoodItemEntity food) {
        MerchantEntity merchant = findMerchant(food.getMerchantId());
        return new FoodItemView(
                food.getId(),
                food.getMerchantId(),
                merchant.getName(),
                food.getName(),
                food.getPrice(),
                food.getImageUrl(),
                food.getSalesCount(),
                food.getOnSale(),
                ResourceStatus.valueOf(food.getStatus()),
                toOffsetDateTime(food.getCreatedAt()),
                toOffsetDateTime(food.getUpdatedAt()));
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    private OffsetDateTime toOffsetDateTime(LocalDateTime value) {
        return value == null ? null : value.atOffset(ZoneOffset.UTC);
    }

    private record Actor(Long userId, String role) {
        boolean isAdmin() {
            return "ADMIN".equals(role);
        }
    }
}
