package com.saktiform.api.configuration;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;

@Configuration
@EnableWebSocketMessageBroker
public class WebsocketConfiguration implements WebSocketMessageBrokerConfigurer {

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")
                .withSockJS();
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler taskScheduler = new org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler();
        taskScheduler.setPoolSize(1);
        taskScheduler.setThreadNamePrefix("wss-heartbeat-thread-");
        taskScheduler.initialize();

        config.enableSimpleBroker("/topic")
                .setHeartbeatValue(new long[] { 10000, 10000 }) // 10s heartbeat
                .setTaskScheduler(taskScheduler);
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registry) {
        registry
                .setMessageSizeLimit(512 * 1024) // 512KB max message size
                .setSendBufferSizeLimit(1024 * 1024) // 1MB send buffer
                .setSendTimeLimit(20 * 1000); // 20 seconds timeout
    }
}
