package com.foodhub.social.web;

import com.foodhub.common.core.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SocialRequestIdentityTest {

    @Test
    void requireUserIdParsesPositiveHeader() {
        assertEquals(7L, SocialRequestIdentity.requireUserId(" 7 "));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " ", "abc", "0", "-1"})
    void requireUserIdRejectsMissingOrInvalidValue(String value) {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> SocialRequestIdentity.requireUserId(value));

        assertEquals("INVALID_USER_ID", exception.getCode());
    }

    @Test
    void optionalUserIdAllowsMissingValueAndParsesPresentValue() {
        assertEquals(null, SocialRequestIdentity.optionalUserId(null));
        assertEquals(7L, SocialRequestIdentity.optionalUserId(" 7 "));
    }

    @Test
    void adminRoleMustBeExactGatewayRole() {
        assertTrue(SocialRequestIdentity.isAdmin("ADMIN"));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> SocialRequestIdentity.requireAdminRole("admin"));

        assertEquals("MODERATION_FORBIDDEN", exception.getCode());
    }
}
