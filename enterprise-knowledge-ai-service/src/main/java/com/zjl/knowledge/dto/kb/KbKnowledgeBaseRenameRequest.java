package com.zjl.knowledge.dto.kb;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class KbKnowledgeBaseRenameRequest {

    @NotBlank
    private String name;
}
