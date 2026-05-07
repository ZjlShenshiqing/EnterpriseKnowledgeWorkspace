package com.zjl.knowledge.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.zjl.knowledge.dto.kb.KbKnowledgeBaseCreateRequest;
import com.zjl.knowledge.dto.kb.KbKnowledgeBasePageRequest;
import com.zjl.knowledge.dto.kb.KbKnowledgeBaseRenameRequest;
import com.zjl.knowledge.dto.kb.KbKnowledgeBaseUpdateRequest;
import com.zjl.knowledge.dto.kb.KbKnowledgeBaseVO;
import com.zjl.knowledge.web.UserContext;

public interface KbKnowledgeBaseService {

    Long create(KbKnowledgeBaseCreateRequest request, UserContext user);

    void update(Long id, KbKnowledgeBaseUpdateRequest request, UserContext user);

    void rename(Long id, KbKnowledgeBaseRenameRequest request, UserContext user);

    void delete(Long id, UserContext user);

    KbKnowledgeBaseVO getById(Long id, UserContext user);

    IPage<KbKnowledgeBaseVO> pageQuery(KbKnowledgeBasePageRequest request, UserContext user);
}
