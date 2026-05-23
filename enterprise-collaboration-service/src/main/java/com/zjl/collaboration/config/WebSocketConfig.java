package com.zjl.collaboration.config;

import com.zjl.collaboration.web.ChatWebSocketHandler;
import com.zjl.collaboration.web.DocWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final ChatWebSocketHandler handler;
    private final DocWebSocketHandler docWebSocketHandler;

    public WebSocketConfig(ChatWebSocketHandler handler, DocWebSocketHandler docWebSocketHandler) {
        this.handler = handler;
        this.docWebSocketHandler = docWebSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/ws/chat").setAllowedOrigins("*");
        registry.addHandler(docWebSocketHandler, "/ws/docs")
                .setAllowedOrigins("*");
    }
}
