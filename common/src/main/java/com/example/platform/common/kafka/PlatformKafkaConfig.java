package com.example.platform.common.kafka;

import com.example.platform.common.events.Topics;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ConsumerRecordRecoverer;
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

    @Bean
    NewTopic paymentEventsTopic() {
        return topic(Topics.PAYMENT_EVENTS);
    }

    @Bean
    NewTopic paymentEventsDlt() {
        return topic(Topics.PAYMENT_EVENTS + ".DLT");
    }

    /** Retry 3x at 1s intervals, then publish the record to "<topic>.DLT". Poison pills skip retries. */
    @Bean
    DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, String> template, MeterRegistry meters) {
        var deadLetters = new DeadLetterPublishingRecoverer(template);
        // Count every dead-lettered record (metric kafka.dead.letters{topic}) so a DLT filling up is visible on a dashboard.
        ConsumerRecordRecoverer recoverer = (record, exception) -> {
            meters.counter("kafka.dead.letters", "topic", record.topic()).increment();
            deadLetters.accept(record, exception);
        };
        var handler = new DefaultErrorHandler(recoverer, new FixedBackOff(1000L, 3L));
        handler.addNotRetryableExceptions(IllegalArgumentException.class);
        return handler;
    }

    private static NewTopic topic(String name) {
        return TopicBuilder.name(name).partitions(3).replicas(1).build();
    }
}
