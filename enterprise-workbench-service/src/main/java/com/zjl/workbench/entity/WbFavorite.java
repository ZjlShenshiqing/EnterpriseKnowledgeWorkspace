package com.zjl.workbench.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("wb_favorite")
public class WbFavorite {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String itemType;

    private Long itemId;

    private String title;

    private LocalDateTime createdAt;
}
