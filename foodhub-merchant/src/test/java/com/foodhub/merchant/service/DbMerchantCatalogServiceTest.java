package com.foodhub.merchant.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodhub.common.redis.RedisKeys;
import com.foodhub.merchant.dto.BusinessStatus;
import com.foodhub.merchant.dto.FoodUpdateRequest;
import com.foodhub.merchant.dto.MerchantUpdateRequest;
import com.foodhub.merchant.dto.ResourceStatus;
import com.foodhub.merchant.entity.CategoryEntity;
import com.foodhub.merchant.entity.FoodItemEntity;
import com.foodhub.merchant.entity.MerchantEntity;
import com.foodhub.merchant.mapper.CategoryMapper;
import com.foodhub.merchant.mapper.FoodItemMapper;
import com.foodhub.merchant.mapper.MerchantMapper;
import com.foodhub.merchant.vo.FoodItemView;
import com.foodhub.merchant.vo.MerchantView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DbMerchantCatalogServiceTest {

    private CategoryMapper categoryMapper;
    private MerchantMapper merchantMapper;
    private FoodItemMapper foodItemMapper;
    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOperations;
    private ObjectMapper objectMapper;
    private DbMerchantCatalogService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        categoryMapper = mock(CategoryMapper.class);
        merchantMapper = mock(MerchantMapper.class);
        foodItemMapper = mock(FoodItemMapper.class);
        redisTemplate = mock(StringRedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        objectMapper = new ObjectMapper().findAndRegisterModules();

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        service = new DbMerchantCatalogService(
                categoryMapper,
                merchantMapper,
                foodItemMapper,
                redisTemplate,
                objectMapper);
    }

    @Test
    void getMerchantReturnsCachedMerchantWithoutQueryingDatabase() throws Exception {
        OffsetDateTime time = OffsetDateTime.parse("2026-01-01T00:00:00Z");
        MerchantView cached = new MerchantView(
                101L,
                7L,
                1L,
                "Hot Meals",
                "Test Shop",
                "Road 1",
                new BigDecimal("116.3975"),
                new BigDecimal("39.9087"),
                new BigDecimal("4.80"),
                1286L,
                BusinessStatus.OPEN,
                ResourceStatus.ACTIVE,
                time,
                time);

        when(valueOperations.get(RedisKeys.merchantDetail(101L)))
                .thenReturn(objectMapper.writeValueAsString(cached));

        MerchantView result = service.getMerchant(101L);

        assertThat(result).isEqualTo(cached);
        verifyNoInteractions(categoryMapper, merchantMapper, foodItemMapper);
    }

    @Test
    void getMerchantWritesCacheAfterDatabaseHit() {
        when(valueOperations.get(RedisKeys.merchantDetail(101L))).thenReturn(null);
        when(merchantMapper.selectById(101L)).thenReturn(merchantEntity());
        when(categoryMapper.selectById(1L)).thenReturn(categoryEntity());

        MerchantView result = service.getMerchant(101L);

        assertThat(result.id()).isEqualTo(101L);
        assertThat(result.categoryName()).isEqualTo("Hot Meals");
        verify(valueOperations).set(
                eq(RedisKeys.merchantDetail(101L)),
                contains("\"name\":\"Test Shop\""),
                any(Duration.class));
    }

    @Test
    void getFoodReturnsCachedFoodWithoutQueryingDatabase() throws Exception {
        OffsetDateTime time = OffsetDateTime.parse("2026-01-01T00:00:00Z");
        FoodItemView cached = new FoodItemView(
                1001L,
                101L,
                "Test Shop",
                "Noodles",
                new BigDecimal("21.90"),
                null,
                88L,
                true,
                ResourceStatus.ACTIVE,
                time,
                time);

        when(valueOperations.get(RedisKeys.foodDetail(1001L)))
                .thenReturn(objectMapper.writeValueAsString(cached));

        FoodItemView result = service.getFood(1001L);

        assertThat(result).isEqualTo(cached);
        verifyNoInteractions(categoryMapper, merchantMapper, foodItemMapper);
    }

    @Test
    void getFoodWritesCacheAfterDatabaseHit() {
        when(valueOperations.get(RedisKeys.foodDetail(1001L))).thenReturn(null);
        when(foodItemMapper.selectById(1001L)).thenReturn(foodEntity());
        when(merchantMapper.selectById(101L)).thenReturn(merchantEntity());

        FoodItemView result = service.getFood(1001L);

        assertThat(result.id()).isEqualTo(1001L);
        assertThat(result.merchantName()).isEqualTo("Test Shop");
        verify(valueOperations).set(
                eq(RedisKeys.foodDetail(1001L)),
                contains("\"name\":\"Noodles\""),
                any(Duration.class));
    }

    @Test
    void updateFoodEvictsFoodDetailCache() {
        when(foodItemMapper.selectById(1001L)).thenReturn(foodEntity());
        when(merchantMapper.selectById(101L)).thenReturn(merchantEntity());

        FoodItemView result = service.updateFood(
                "7",
                "MERCHANT",
                1001L,
                new FoodUpdateRequest(null, new BigDecimal("25.50"), null, false, null));

        assertThat(result.price()).isEqualByComparingTo("25.50");
        assertThat(result.onSale()).isFalse();
        verify(foodItemMapper).updateById(any(FoodItemEntity.class));
        verify(redisTemplate).delete(RedisKeys.foodDetail(1001L));
    }

    @Test
    void updateMerchantEvictsMerchantAndOwnedFoodCaches() {
        when(merchantMapper.selectById(101L)).thenReturn(merchantEntity());
        when(categoryMapper.selectById(1L)).thenReturn(categoryEntity());
        when(foodItemMapper.selectList(any())).thenReturn(List.of(foodEntity()));

        MerchantView result = service.updateMerchant(
                "7",
                "MERCHANT",
                101L,
                new MerchantUpdateRequest(
                        "Updated Shop",
                        null,
                        null,
                        null,
                        null,
                        BusinessStatus.CLOSED,
                        null));

        assertThat(result.name()).isEqualTo("Updated Shop");
        assertThat(result.businessStatus()).isEqualTo(BusinessStatus.CLOSED);
        verify(merchantMapper).updateById(any(MerchantEntity.class));
        verify(redisTemplate).delete(RedisKeys.merchantDetail(101L));
        verify(redisTemplate).delete(RedisKeys.foodDetail(1001L));
    }

    private MerchantEntity merchantEntity() {
        MerchantEntity merchant = new MerchantEntity();
        merchant.setId(101L);
        merchant.setOwnerUserId(7L);
        merchant.setCategoryId(1L);
        merchant.setName("Test Shop");
        merchant.setAddress("Road 1");
        merchant.setLongitude(new BigDecimal("116.3975"));
        merchant.setLatitude(new BigDecimal("39.9087"));
        merchant.setRating(new BigDecimal("4.80"));
        merchant.setSalesCount(1286L);
        merchant.setBusinessStatus(BusinessStatus.OPEN.name());
        merchant.setStatus(ResourceStatus.ACTIVE.name());
        merchant.setCreatedAt(LocalDateTime.parse("2026-01-01T00:00:00"));
        merchant.setUpdatedAt(LocalDateTime.parse("2026-01-01T00:00:00"));
        return merchant;
    }

    private FoodItemEntity foodEntity() {
        FoodItemEntity food = new FoodItemEntity();
        food.setId(1001L);
        food.setMerchantId(101L);
        food.setName("Noodles");
        food.setPrice(new BigDecimal("21.90"));
        food.setImageUrl(null);
        food.setSalesCount(88L);
        food.setOnSale(true);
        food.setStatus(ResourceStatus.ACTIVE.name());
        food.setCreatedAt(LocalDateTime.parse("2026-01-01T00:00:00"));
        food.setUpdatedAt(LocalDateTime.parse("2026-01-01T00:00:00"));
        return food;
    }

    private CategoryEntity categoryEntity() {
        CategoryEntity category = new CategoryEntity();
        category.setId(1L);
        category.setName("Hot Meals");
        category.setSortOrder(10);
        category.setStatus(ResourceStatus.ACTIVE.name());
        return category;
    }
}
