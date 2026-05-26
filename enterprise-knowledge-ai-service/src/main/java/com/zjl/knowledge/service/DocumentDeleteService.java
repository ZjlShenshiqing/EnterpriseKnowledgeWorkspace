package com.zjl.knowledge.service;

import com.zjl.knowledge.web.UserContext;

/**
 * 文档删除服务
 */
public interface DocumentDeleteService {

    void deleteVisible(Long id, UserContext user);
}
