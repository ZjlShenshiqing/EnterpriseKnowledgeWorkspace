package com.zjl.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class UserContextTest {

    @BeforeEach
    @AfterEach
    void clearContext() {
        UserContext.clear();
    }

    @Test
    void fourParamSetWritesAllFields() {
        UserContext.set(100L, "zhangsan", true, List.of("ROLE_ADMIN", "PERM_READ"));

        assertThat(UserContext.userId()).isEqualTo(100L);
        assertThat(UserContext.username()).isEqualTo("zhangsan");
        assertThat(UserContext.isAdmin()).isTrue();
        assertThat(UserContext.authorities()).containsExactly("ROLE_ADMIN", "PERM_READ");
    }

    @Test
    void fourParamSetWritesNonAdminCorrectly() {
        UserContext.set(200L, "lisi", false, List.of("ROLE_USER"));

        assertThat(UserContext.isAdmin()).isFalse();
    }

    @Test
    void clearRemovesAllFields() {
        UserContext.set(100L, "zhangsan", true, List.of("ROLE_ADMIN"));

        UserContext.clear();

        assertThat(UserContext.userId()).isNull();
        assertThat(UserContext.username()).isNull();
        assertThat(UserContext.isAdmin()).isNull();
        assertThat(UserContext.authorities()).isNull();
    }

    @Test
    void userIdReturnsNullWhenNotSet() {
        assertThat(UserContext.userId()).isNull();
    }

    @Test
    void isAdminReturnsNullWhenNotSet() {
        assertThat(UserContext.isAdmin()).isNull();
    }

    @Test
    void isAdminIsFalseNotNullWhenExplicitlyFalse() {
        UserContext.set(300L, "wangwu", false, List.of());

        assertThat(UserContext.isAdmin()).isNotNull();
        assertThat(UserContext.isAdmin()).isFalse();
    }
}
