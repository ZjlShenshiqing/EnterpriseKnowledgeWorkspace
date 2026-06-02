package com.zjl.collaboration.workflow.service;

import com.zjl.collaboration.workflow.dto.ApprovalCreateRequest;
import com.zjl.collaboration.workflow.vo.ApprovalCreateVO;
import com.zjl.collaboration.workflow.vo.ApprovalDetailVO;
import com.zjl.collaboration.workflow.vo.ApprovalListVO;

import java.util.List;

public interface ApprovalApplicationService {
    ApprovalCreateVO create(ApprovalCreateRequest request, Long userId);

    List<ApprovalListVO> listMine(Long userId);

    List<ApprovalListVO> listAll();

    ApprovalDetailVO detail(Long approvalId, Long userId, boolean admin);
}
