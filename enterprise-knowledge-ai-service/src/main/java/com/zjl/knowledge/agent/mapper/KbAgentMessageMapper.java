package com.zjl.knowledge.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zjl.knowledge.agent.entity.KbAgentMessage;
import org.apache.ibatis.annotations.Mapper;

/**
 * Agent 消息 Mapper。
 */
@Mapper
public interface KbAgentMessageMapper extends BaseMapper<KbAgentMessage> {
}
