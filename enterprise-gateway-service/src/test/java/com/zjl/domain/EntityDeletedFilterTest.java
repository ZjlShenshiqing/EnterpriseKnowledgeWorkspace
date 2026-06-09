package com.zjl.domain;

import org.hibernate.annotations.SQLRestriction;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;

class EntityDeletedFilterTest {

    @Test
    void sysUserHasSqlRestrictionAnnotation() {
        SQLRestriction annotation = SysUser.class.getAnnotation(SQLRestriction.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).isEqualTo("deleted = 0");
    }

    @Test
    void sysUserHasDeletedField() throws NoSuchFieldException {
        Field field = SysUser.class.getDeclaredField("deleted");
        assertThat(field.getType()).isEqualTo(Integer.class);
    }

    @Test
    void sysRoleHasSqlRestrictionAnnotation() {
        SQLRestriction annotation = SysRole.class.getAnnotation(SQLRestriction.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).isEqualTo("deleted = 0");
    }

    @Test
    void sysRoleHasDeletedField() throws NoSuchFieldException {
        Field field = SysRole.class.getDeclaredField("deleted");
        assertThat(field.getType()).isEqualTo(Integer.class);
    }

    @Test
    void sysDeptHasSqlRestrictionAnnotation() {
        SQLRestriction annotation = SysDept.class.getAnnotation(SQLRestriction.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).isEqualTo("deleted = 0");
    }

    @Test
    void sysDeptHasDeletedField() throws NoSuchFieldException {
        Field field = SysDept.class.getDeclaredField("deleted");
        assertThat(field.getType()).isEqualTo(Integer.class);
    }
}
