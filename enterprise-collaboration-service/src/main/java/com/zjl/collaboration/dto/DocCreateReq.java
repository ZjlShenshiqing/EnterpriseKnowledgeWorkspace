package com.zjl.collaboration.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class DocCreateReq {
    @NotBlank(message = "文档标题不能为空")
    private String title;
}
