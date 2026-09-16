package com.foodhub.merchant.service;

import com.foodhub.common.core.BusinessException;
import com.foodhub.merchant.dto.BusinessStatus;
import com.foodhub.merchant.dto.FoodCreateRequest;
import com.foodhub.merchant.dto.FoodUpdateRequest;
import com.foodhub.merchant.dto.MerchantCreateRequest;
import com.foodhub.merchant.dto.MerchantUpdateRequest;
import com.foodhub.merchant.dto.ResourceStatus;
import com.foodhub.merchant.vo.CategoryView;
import com.foodhub.merchant.vo.FoodItemView;
import com.foodhub.merchant.vo.MerchantView;
import com.foodhub.merchant.vo.PageResponse;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Service
@Profile("mock")
public class InMemoryMerchantCatalogService implements MerchantCatalogService {

    private static final int MAX_PAGE_SIZE = 100;

    private final Map<Long, CategoryState> categories = new ConcurrentHashMap<>();
    private final Map<Long, MerchantState> merchants = new ConcurrentHashMap<>();
    private final Map<Long, FoodState> foods = new ConcurrentHashMap<>();
    private final AtomicLong merchantSequence = new AtomicLong(1000);
    private final AtomicLong foodSequence = new AtomicLong(5000);

    public InMemoryMerchantCatalogService() {
        seedData();
    }

    public List<CategoryView> listCategories() {
        return categories.values().stream()
                .filter(category -> category.status == ResourceStatus.ACTIVE)
                .sorted(Comparator.comparingInt(category -> category.sortOrder))
                .map(this::toCategoryView)
                .toList();
    }

    public PageResponse<MerchantView> listMerchants(
            String keyword,
            Long categoryId,
            String sort,
            int page,
            int size) {
        ensureCategoryExistsIfPresent(categoryId);
        List<MerchantView> items = merchants.values().stream()
                .filter(this::isPublicMerchant)
                .filter(merchant -> categoryId == null || merchant.categoryId.equals(categoryId))
                .filter(merchant -> matches(keyword, merchant.name, merchant.address))
                .sorted(merchantComparator(sort))
                .map(this::toMerchantView)
                .toList();
        return page(items, page, size);
    }

    public MerchantView getMerchant(Long merchantId) {
        MerchantState merchant = findMerchant(merchantId);
        if (!isPublicMerchant(merchant)) {
            throw new BusinessException("MERCHANT_NOT_FOUND", "Merchant does not exist or is unavailable");
        }
        return toMerchantView(merchant);
    }

    public synchronized MerchantView createMerchant(
            String rawUserId,
            String rawRole,
            MerchantCreateRequest request) {
        Actor actor = requireWriter(rawUserId, rawRole);
        CategoryState category = findActiveCategory(request.categoryId());
        OffsetDateTime now = now();
        MerchantState merchant = new MerchantState(
                merchantSequence.incrementAndGet(),
                actor.userId(),
                category.id,
                request.name().trim(),
                request.address().trim(),
                request.longitude(),
                request.latitude(),
                new BigDecimal("5.0"),
                0L,
                BusinessStatus.CLOSED,
                ResourceStatus.ACTIVE,
                now,
                now);
        merchants.put(merchant.id, merchant);
        return toMerchantView(merchant);
    }

    public synchronized MerchantView updateMerchant(
            String rawUserId,
            String rawRole,
            Long merchantId,
            MerchantUpdateRequest request) {
        Actor actor = requireWriter(rawUserId, rawRole);
        MerchantState merchant = findMerchant(merchantId);
        requireOwnerOrAdmin(actor, merchant.ownerUserId);
        if (request.categoryId() != null) {
            findActiveCategory(request.categoryId());
            merchant.categoryId = request.categoryId();
        }
        if (StringUtils.hasText(request.name())) {
            merchant.name = request.name().trim();
        }
        if (StringUtils.hasText(request.address())) {
            merchant.address = request.address().trim();
        }
        if (request.longitude() != null) {
            merchant.longitude = request.longitude();
        }
        if (request.latitude() != null) {
            merchant.latitude = request.latitude();
        }
        if (request.businessStatus() != null) {
            merchant.businessStatus = request.businessStatus();
        }
        if (request.status() != null) {
            merchant.status = request.status();
        }
        merchant.updatedAt = now();
        return toMerchantView(merchant);
    }

    public PageResponse<FoodItemView> listFoods(
            Long merchantId,
            Long categoryId,
            String keyword,
            String sort,
            int page,
            int size) {
        ensureMerchantExistsIfPresent(merchantId);
        ensureCategoryExistsIfPresent(categoryId);
        List<FoodItemView> items = foods.values().stream()
                .filter(this::isPublicFood)
                .filter(food -> merchantId == null || food.merchantId.equals(merchantId))
                .filter(food -> categoryId == null || findMerchant(food.merchantId).categoryId.equals(categoryId))
                .filter(food -> matches(keyword, food.name, findMerchant(food.merchantId).name))
                .sorted(foodComparator(sort))
                .map(this::toFoodItemView)
                .toList();
        return page(items, page, size);
    }

    public FoodItemView getFood(Long foodId) {
        FoodState food = findFood(foodId);
        if (!isPublicFood(food)) {
            throw new BusinessException("FOOD_NOT_FOUND", "Food item does not exist or is unavailable");
        }
        return toFoodItemView(food);
    }

    public synchronized FoodItemView createFood(
            String rawUserId,
            String rawRole,
            FoodCreateRequest request) {
        Actor actor = requireWriter(rawUserId, rawRole);
        MerchantState merchant = findMerchant(request.merchantId());
        requireOwnerOrAdmin(actor, merchant.ownerUserId);
        if (merchant.status != ResourceStatus.ACTIVE) {
            throw new BusinessException("MERCHANT_UNAVAILABLE", "Food cannot be added to an inactive merchant");
        }
        OffsetDateTime now = now();
        FoodState food = new FoodState(
                foodSequence.incrementAndGet(),
                merchant.id,
                request.name().trim(),
                request.price(),
                trimToNull(request.imageUrl()),
                0L,
                true,
                ResourceStatus.ACTIVE,
                now,
                now);
        foods.put(food.id, food);
        return toFoodItemView(food);
    }

    public synchronized FoodItemView updateFood(
            String rawUserId,
            String rawRole,
            Long foodId,
            FoodUpdateRequest request) {
        Actor actor = requireWriter(rawUserId, rawRole);
        FoodState food = findFood(foodId);
        MerchantState merchant = findMerchant(food.merchantId);
        requireOwnerOrAdmin(actor, merchant.ownerUserId);
        if (StringUtils.hasText(request.name())) {
            food.name = request.name().trim();
        }
        if (request.price() != null) {
            food.price = request.price();
        }
        if (request.imageUrl() != null) {
            food.imageUrl = trimToNull(request.imageUrl());
        }
        if (request.onSale() != null) {
            food.onSale = request.onSale();
        }
        if (request.status() != null) {
            food.status = request.status();
        }
        food.updatedAt = now();
        return toFoodItemView(food);
    }

    private void seedData() {
        categories.put(1L, new CategoryState(1L, "川湘菜", 10, ResourceStatus.ACTIVE));
        categories.put(2L, new CategoryState(2L, "粤式茶点", 20, ResourceStatus.ACTIVE));
        categories.put(3L, new CategoryState(3L, "咖啡甜品", 30, ResourceStatus.ACTIVE));

        OffsetDateTime now = now();
        merchants.put(101L, new MerchantState(
                101L, 7L, 1L, "青椒巷小馆", "滨河路 18 号", bd("116.3975"), bd("39.9087"),
                bd("4.8"), 1286L, BusinessStatus.OPEN, ResourceStatus.ACTIVE, now, now));
        merchants.put(102L, new MerchantState(
                102L, 8L, 2L, "城南早茶", "南湖街 88 号", bd("116.4102"), bd("39.9011"),
                bd("4.6"), 932L, BusinessStatus.OPEN, ResourceStatus.ACTIVE, now, now));
        merchants.put(103L, new MerchantState(
                103L, 7L, 3L, "河岸咖啡", "星桥巷 6 号", bd("116.3859"), bd("39.9172"),
                bd("4.7"), 521L, BusinessStatus.CLOSED, ResourceStatus.ACTIVE, now, now));

        foods.put(1001L, new FoodState(1001L, 101L, "招牌辣子鸡", bd("42.00"), null,
                420L, true, ResourceStatus.ACTIVE, now, now));
        foods.put(1002L, new FoodState(1002L, 101L, "砂锅小酥肉", bd("36.00"), null,
                315L, true, ResourceStatus.ACTIVE, now, now));
        foods.put(1003L, new FoodState(1003L, 102L, "虾饺皇", bd("29.00"), null,
                508L, true, ResourceStatus.ACTIVE, now, now));
        foods.put(1004L, new FoodState(1004L, 103L, "焦糖拿铁", bd("25.00"), null,
                198L, true, ResourceStatus.ACTIVE, now, now));
    }

    private <T> PageResponse<T> page(List<T> items, int rawPage, int rawSize) {
        List<T> pageItems = new ArrayList<>(items);
        int page = Math.max(rawPage, 1);
        int size = Math.min(Math.max(rawSize, 1), MAX_PAGE_SIZE);
        int from = Math.min((page - 1) * size, pageItems.size());
        int to = Math.min(from + size, pageItems.size());
        return new PageResponse<>(pageItems.subList(from, to), page, size, pageItems.size());
    }

    private Comparator<MerchantState> merchantComparator(String sort) {
        if ("sales".equalsIgnoreCase(sort)) {
            return Comparator.comparingLong((MerchantState merchant) -> merchant.salesCount).reversed()
                    .thenComparing(merchant -> merchant.id);
        }
        return Comparator.comparing((MerchantState merchant) -> merchant.rating).reversed()
                .thenComparing(merchant -> merchant.id);
    }

    private Comparator<FoodState> foodComparator(String sort) {
        if ("price".equalsIgnoreCase(sort)) {
            return Comparator.comparing(food -> food.price);
        }
        return Comparator.comparingLong((FoodState food) -> food.salesCount).reversed()
                .thenComparing(food -> food.id);
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

    private boolean isPublicMerchant(MerchantState merchant) {
        return merchant.status == ResourceStatus.ACTIVE;
    }

    private boolean isPublicFood(FoodState food) {
        MerchantState merchant = findMerchant(food.merchantId);
        return food.status == ResourceStatus.ACTIVE
                && Boolean.TRUE.equals(food.onSale)
                && merchant.status == ResourceStatus.ACTIVE;
    }

    private CategoryState findActiveCategory(Long categoryId) {
        CategoryState category = categories.get(categoryId);
        if (category == null || category.status != ResourceStatus.ACTIVE) {
            throw new BusinessException("CATEGORY_NOT_FOUND", "Category does not exist or is inactive");
        }
        return category;
    }

    private MerchantState findMerchant(Long merchantId) {
        MerchantState merchant = merchants.get(merchantId);
        if (merchant == null) {
            throw new BusinessException("MERCHANT_NOT_FOUND", "Merchant does not exist");
        }
        return merchant;
    }

    private FoodState findFood(Long foodId) {
        FoodState food = foods.get(foodId);
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

    private MerchantView toMerchantView(MerchantState merchant) {
        CategoryState category = categories.get(merchant.categoryId);
        return new MerchantView(
                merchant.id,
                merchant.ownerUserId,
                merchant.categoryId,
                category == null ? null : category.name,
                merchant.name,
                merchant.address,
                merchant.longitude,
                merchant.latitude,
                merchant.rating,
                merchant.salesCount,
                merchant.businessStatus,
                merchant.status,
                merchant.createdAt,
                merchant.updatedAt);
    }

    private CategoryView toCategoryView(CategoryState category) {
        return new CategoryView(category.id, category.name, category.sortOrder);
    }

    private FoodItemView toFoodItemView(FoodState food) {
        MerchantState merchant = findMerchant(food.merchantId);
        return new FoodItemView(
                food.id,
                food.merchantId,
                merchant.name,
                food.name,
                food.price,
                food.imageUrl,
                food.salesCount,
                food.onSale,
                food.status,
                food.createdAt,
                food.updatedAt);
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }

    private BigDecimal bd(String value) {
        return new BigDecimal(value);
    }

    private record Actor(Long userId, String role) {
        boolean isAdmin() {
            return "ADMIN".equals(role);
        }
    }

    private static final class CategoryState {
        private final Long id;
        private final String name;
        private final int sortOrder;
        private final ResourceStatus status;

        private CategoryState(Long id, String name, int sortOrder, ResourceStatus status) {
            this.id = id;
            this.name = name;
            this.sortOrder = sortOrder;
            this.status = status;
        }
    }

    private static final class MerchantState {
        private final Long id;
        private final Long ownerUserId;
        private Long categoryId;
        private String name;
        private String address;
        private BigDecimal longitude;
        private BigDecimal latitude;
        private final BigDecimal rating;
        private final Long salesCount;
        private BusinessStatus businessStatus;
        private ResourceStatus status;
        private final OffsetDateTime createdAt;
        private OffsetDateTime updatedAt;

        private MerchantState(
                Long id,
                Long ownerUserId,
                Long categoryId,
                String name,
                String address,
                BigDecimal longitude,
                BigDecimal latitude,
                BigDecimal rating,
                Long salesCount,
                BusinessStatus businessStatus,
                ResourceStatus status,
                OffsetDateTime createdAt,
                OffsetDateTime updatedAt) {
            this.id = id;
            this.ownerUserId = ownerUserId;
            this.categoryId = categoryId;
            this.name = name;
            this.address = address;
            this.longitude = longitude;
            this.latitude = latitude;
            this.rating = rating;
            this.salesCount = salesCount;
            this.businessStatus = businessStatus;
            this.status = status;
            this.createdAt = createdAt;
            this.updatedAt = updatedAt;
        }
    }

    private static final class FoodState {
        private final Long id;
        private final Long merchantId;
        private String name;
        private BigDecimal price;
        private String imageUrl;
        private final Long salesCount;
        private Boolean onSale;
        private ResourceStatus status;
        private final OffsetDateTime createdAt;
        private OffsetDateTime updatedAt;

        private FoodState(
                Long id,
                Long merchantId,
                String name,
                BigDecimal price,
                String imageUrl,
                Long salesCount,
                Boolean onSale,
                ResourceStatus status,
                OffsetDateTime createdAt,
                OffsetDateTime updatedAt) {
            this.id = id;
            this.merchantId = merchantId;
            this.name = name;
            this.price = price;
            this.imageUrl = imageUrl;
            this.salesCount = salesCount;
            this.onSale = onSale;
            this.status = status;
            this.createdAt = createdAt;
            this.updatedAt = updatedAt;
        }
    }
}
