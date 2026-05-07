package com.zjl.knowledge.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 分类创建或更新请求
 */
@Data
public class KbCategoryRequest {

    /**
     * 分类名称
     */
    @NotBlank
    private String categoryName;

    /**
     * 父分类 ID
     */
    private Long parentId;

    /**
     * 分类类型
     */
    private String categoryType;

    /**
     * 部门 ID
     */
    private Long departmentId;

    /**
     * 排序
     */
    private Integer sortOrder;
}
