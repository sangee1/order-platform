package com.example.platform.common.kafka;

import com.example.platform.common.events.Topics;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

/** Topics and consumer error handling shared by every service. Import it with @Import. */
@Configuration
public class PlatformKafkaConfig {

    @Bean
    NewTopic orderEventsTopic() {
        return topic(Topics.ORDER_EVENTS);
    }

    @Bean
    NewTopic orderEventsDlt() {
        return topic(Topics.ORDER_EVENTS + ".DLT");
    }

    @Bean
    NewTopic inventoryEventsTopic() {
        return topic(Topics.INVENTORY_EVENTS);
    }

    @Bean
    NewTopic inventoryEventsDlt() {
        return topic(Topics.INVENTORY_EVENTS + ".DLT");
    }

    /** Retry 3x at 1s intervals, then publish the record to "<topic>.DLT". Poison pills skip retries. */
    @Bean
    DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, String> template) {
        var handler = new DefaultErrorHandler(new DeadLetterPublishingRecoverer(template), new FixedBackOff(1000L, 3L));
        handler.addNotRetryableExceptions(IllegalArgumentException.class);
        return handler;
    }

    private static NewTopic topic(String name) {
        return TopicBuilder.name(name).partitions(3).replicas(1).build();
    }
}
