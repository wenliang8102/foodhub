package com.foodhub.social.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CreatePostRequestValidationTest {

    private static Validator validator;

    @BeforeAll
    static void createValidator() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Test
    void validRequestPassesBeanValidation() {
        CreatePostRequest request = new CreatePostRequest(
                "分享一家本地餐厅",
                List.of("https://example.com/food.jpg"),
                12L);

        assertTrue(validator.validate(request).isEmpty());
    }

    @Test
    void blankContentIsRejected() {
        CreatePostRequest request = new CreatePostRequest("   ", List.of(), null);

        Set<ConstraintViolation<CreatePostRequest>> violations = validator.validate(request);

        assertFalse(violations.isEmpty());
    }

    @Test
    void moreThanNineImagesIsRejected() {
        CreatePostRequest request = new CreatePostRequest(
                "正文",
                java.util.stream.IntStream.range(0, 10)
                        .mapToObj(index -> "https://example.com/" + index + ".jpg")
                        .toList(),
                null);

        assertFalse(validator.validate(request).isEmpty());
    }

    @Test
    void nonPositiveMerchantIdIsRejected() {
        CreatePostRequest request = new CreatePostRequest("正文", List.of(), 0L);

        assertFalse(validator.validate(request).isEmpty());
    }
}
