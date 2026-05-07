package com.zjl.knowledge.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.zjl.knowledge.entity.KbCategory;
import com.zjl.knowledge.mapper.KbCategoryMapper;
import com.zjl.knowledge.service.KbCategoryService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 知识分类服务实现。
 */
@Service
public class KbCategoryServiceImpl extends ServiceImpl<KbCategoryMapper, KbCategory> implements KbCategoryService {

    /**
     * 插入前填充时间字段。
     */
    @Override
    public boolean save(KbCategory entity) {
        LocalDateTime now = LocalDateTime.now();
        if (entity.getCreatedAt() == null) {
            entity.setCreatedAt(now);
        }
        entity.setUpdatedAt(now);
        return super.save(entity);
    }

    /**
     * 更新前填充时间字段。
     */
    @Override
    public boolean updateById(KbCategory entity) {
        entity.setUpdatedAt(LocalDateTime.now());
        return super.updateById(entity);
    }
}
