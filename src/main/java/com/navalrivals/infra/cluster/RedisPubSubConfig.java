package com.navalrivals.infra.cluster;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

@Configuration
@RequiredArgsConstructor
public class RedisPubSubConfig {

    private final ClusterEventListener clusterEventListener;
    private final LobbySSEClusterListener lobbySSEClusterListener;

    @Bean
    public RedisMessageListenerContainer listenerContainer(RedisConnectionFactory factory){
        var container = new RedisMessageListenerContainer();
        container.setConnectionFactory(factory);
        container.addMessageListener(clusterEventListener, new ChannelTopic("naval-rivals:ws-events"));
        container.addMessageListener(lobbySSEClusterListener, new ChannelTopic("naval-rivals:lobby-events"));
        return container;
    }
}
