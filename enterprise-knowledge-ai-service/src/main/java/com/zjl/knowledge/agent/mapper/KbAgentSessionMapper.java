package com.zjl.knowledge.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zjl.knowledge.agent.entity.KbAgentSession;
import org.apache.ibatis.annotations.Mapper;

/**
 * Agent 会话 Mapper
 */
@Mapper
public interface KbAgentSessionMapper extends BaseMapper<KbAgentSession> {
}
