package com.zjl.collaboration.service;

import org.springframework.web.socket.WebSocketSession;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * IM 消息消费者接口
 */
public interface ImMessageConsumer {

    Map<Long, WebSocketSession> onlineUsers = new ConcurrentHashMap<>();

    void onMessage(String payload);
}
