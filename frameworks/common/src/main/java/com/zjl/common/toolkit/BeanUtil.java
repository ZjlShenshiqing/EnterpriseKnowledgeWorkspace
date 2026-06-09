/*
 * Copyright (c) 2025-2026 zhangjlk
 * All rights reserved.
 */
package com.zjl.common.toolkit;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.beans.BeanUtils;

import java.lang.reflect.Array;
import java.util.*;

/**
 * 对象属性复制工具类
 *
 * @author zhangjlk
 * @date 2025/9/18 10:37
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class BeanUtil {

    /**
     * 对象属性复制
     *
     * @param source  数据对象
     * @param target  目标对象
     * @param <T>    目标对象的类型
     * @param <S>    源对象的类型
     * @return 转换后对象
     */
    public static <T, S> T convert(S source, T target) {
        Optional.ofNullable(source)
                .ifPresent(each -> BeanUtils.copyProperties(each, target));
        return target;
    }

    /**
     * 将源对象转换为指定类型的新对象
     *
     * @param source 源对象（例如 UserEntity）
     * @param clazz  目标类的 Class 对象（例如 UserDTO.class）
     * @param <T>    目标对象的类型
     * @param <S>    源对象的类型
     * @return 返回一个新创建的目标类型对象，填充了 source 的数据；如果 source 为 null，则返回 null
     */
    public static <T, S> T convert(S source, Class<T> clazz) {
        if (source == null) {
            return null;
        }
        try {
            T target = clazz.getDeclaredConstructor().newInstance();
            BeanUtils.copyProperties(source, target);
            return target;
        } catch (Exception e) {
            throw new RuntimeException("对象转换失败", e);
        }
    }

    /**
     * 复制多个对象（List版本）
     *
     * @param sources 数据对象
     * @param clazz   复制目标类型
     * @param <T>     目标对象的类型
     * @param <S>     源对象的类型
     * @return        转换后对象集合
     */
    public static <T, S> List<T> convert(List<S> sources, Class<T> clazz) {
        if (sources == null) {
            return null;
        }
        List<T> targetList = new ArrayList<>(sources.size());
        for (S source : sources) {
            targetList.add(convert(source, clazz));
        }
        return targetList;
    }

    /**
     * 复制多个对象（Set版本）
     *
     * @param sources 数据对象
     * @param clazz   复制目标类型
     * @param <T>     目标对象的类型
     * @param <S>     源对象的类型
     * @return        转换后对象集合
     */
    public static <T, S> Set<T> convert(Set<S> sources, Class<T> clazz) {
        if (sources == null) {
            return null;
        }
        Set<T> targetSet = new HashSet<>(sources.size());
        for (S source : sources) {
            targetSet.add(convert(source, clazz));
        }
        return targetSet;
    }

    /**
     * 复制多个对象（数组版本）
     *
     * @param sources 数据对象
     * @param clazz   复制目标类型
     * @param <T>     目标对象的类型
     * @param <S>     源对象的类型
     * @return        转换后对象集合
     */
    public static <T, S> T[] convert(S[] sources, Class<T> clazz) {
        if (sources == null) {
            return null;
        }
        T[] targetArray = (T[]) Array.newInstance(clazz, sources.length);
        for (int i = 0; i < targetArray.length; i++) {
            targetArray[i] = convert(sources[i], clazz);
        }
        return targetArray;
    }
}