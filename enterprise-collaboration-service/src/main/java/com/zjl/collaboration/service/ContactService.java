package com.zjl.collaboration.service;

import java.util.List;
import java.util.Map;

/**
 * 联系人业务服务。
 */
public interface ContactService {

    /**
     * 查询部门。
     *
     * @return 部门列表
     */
    List<Map<String, Object>> listDepartments();

    /**
     * 查询用户。
     *
     * @param deptId  部门 ID
     * @param keyword 关键词
     * @param limit   限制数量
     * @return 用户列表
     */
    List<Map<String, Object>> listUsers(Long deptId, String keyword, int limit);
}
