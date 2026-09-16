package com.foodhub.merchant.service;

import com.foodhub.common.core.BusinessException;
import com.foodhub.merchant.dto.BusinessStatus;
import com.foodhub.merchant.dto.FoodCreateRequest;
import com.foodhub.merchant.dto.MerchantCreateRequest;
import com.foodhub.merchant.dto.MerchantUpdateRequest;
import com.foodhub.merchant.vo.MerchantView;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MerchantCatalogServiceTest {

    private final MerchantCatalogService service = new InMemoryMerchantCatalogService();

    @Test
    void listsSeededPublicMerchantsWithPagination() {
        var page = service.listMerchants(null, null, "rating", 1, 2);

        assertThat(page.items()).hasSize(2);
        assertThat(page.total()).isEqualTo(3);
        assertThat(page.items().get(0).rating()).isGreaterThanOrEqualTo(page.items().get(1).rating());
    }

    @Test
    void merchantCanCreateAndUpdateOwnedMerchant() {
        MerchantView created = service.createMerchant("42", "MERCHANT", new MerchantCreateRequest(
                "New Test Shop",
                1L,
                "College Road 1",
                new BigDecimal("116.3000"),
                new BigDecimal("39.9000")));

        MerchantView updated = service.updateMerchant("42", "MERCHANT", created.id(), new MerchantUpdateRequest(
                null,
                null,
                null,
                null,
                null,
                BusinessStatus.OPEN,
                null));

        assertThat(created.ownerUserId()).isEqualTo(42L);
        assertThat(created.name()).isEqualTo("New Test Shop");
        assertThat(updated.businessStatus()).isEqualTo(BusinessStatus.OPEN);
    }

    @Test
    void merchantCannotManageAnotherOwnersFood() {
        FoodCreateRequest request = new FoodCreateRequest(
                102L,
                "Unauthorized Food",
                new BigDecimal("19.90"),
                null);

        assertThatThrownBy(() -> service.createFood("7", "MERCHANT", request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("own merchant");
    }

    @Test
    void userRoleCannotWrite() {
        MerchantCreateRequest request = new MerchantCreateRequest(
                "User Role Shop",
                1L,
                "Test Road 1",
                new BigDecimal("116.3000"),
                new BigDecimal("39.9000"));

        assertThatThrownBy(() -> service.createMerchant("9", "USER", request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("MERCHANT or ADMIN");
    }
}
