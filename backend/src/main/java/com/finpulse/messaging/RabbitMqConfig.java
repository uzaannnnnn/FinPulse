package com.finpulse.messaging;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ topology declarations (exchanges, queues, DLQs, bindings) and JSON message converter.
 */
@Configuration
public class RabbitMqConfig {

    public static final String EVENTS_EXCHANGE = "finpulse.events";
    public static final String DLX_EXCHANGE = "finpulse.dlx";

    public static final String TRANSACTION_CREATED_QUEUE = "finpulse.transaction.created";
    public static final String TRANSACTION_CREATED_ROUTING_KEY = "finpulse.transaction.created";
    public static final String TRANSACTION_CREATED_DLQ = "finpulse.transaction.created.dlq";

    public static final String REPORT_EXPORT_QUEUE = "finpulse.report.export";
    public static final String REPORT_EXPORT_ROUTING_KEY = "finpulse.report.export";
    public static final String REPORT_EXPORT_DLQ = "finpulse.report.export.dlq";

    @Bean
    public TopicExchange finpulseEventsExchange() {
        return new TopicExchange(EVENTS_EXCHANGE, true, false);
    }

    @Bean
    public DirectExchange finpulseDlxExchange() {
        return new DirectExchange(DLX_EXCHANGE, true, false);
    }

    @Bean
    public Queue transactionCreatedQueue() {
        return QueueBuilder.durable(TRANSACTION_CREATED_QUEUE)
                .withArgument("x-dead-letter-exchange", DLX_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", TRANSACTION_CREATED_DLQ)
                .build();
    }

    @Bean
    public Queue transactionCreatedDlq() {
        return QueueBuilder.durable(TRANSACTION_CREATED_DLQ).build();
    }

    @Bean
    public Binding transactionCreatedBinding(
            Queue transactionCreatedQueue,
            TopicExchange finpulseEventsExchange
    ) {
        return BindingBuilder.bind(transactionCreatedQueue)
                .to(finpulseEventsExchange)
                .with(TRANSACTION_CREATED_ROUTING_KEY);
    }

    @Bean
    public Binding transactionCreatedDlqBinding(
            Queue transactionCreatedDlq,
            DirectExchange finpulseDlxExchange
    ) {
        return BindingBuilder.bind(transactionCreatedDlq)
                .to(finpulseDlxExchange)
                .with(TRANSACTION_CREATED_DLQ);
    }

    @Bean
    public Queue reportExportQueue() {
        return QueueBuilder.durable(REPORT_EXPORT_QUEUE)
                .withArgument("x-dead-letter-exchange", DLX_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", REPORT_EXPORT_DLQ)
                .build();
    }

    @Bean
    public Queue reportExportDlq() {
        return QueueBuilder.durable(REPORT_EXPORT_DLQ).build();
    }

    @Bean
    public Binding reportExportBinding(
            Queue reportExportQueue,
            TopicExchange finpulseEventsExchange
    ) {
        return BindingBuilder.bind(reportExportQueue)
                .to(finpulseEventsExchange)
                .with(REPORT_EXPORT_ROUTING_KEY);
    }

    @Bean
    public Binding reportExportDlqBinding(
            Queue reportExportDlq,
            DirectExchange finpulseDlxExchange
    ) {
        return BindingBuilder.bind(reportExportDlq)
                .to(finpulseDlxExchange)
                .with(REPORT_EXPORT_DLQ);
    }

    @Bean
    public MessageConverter jackson2JsonMessageConverter() {
        ObjectMapper amqpObjectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        return new Jackson2JsonMessageConverter(amqpObjectMapper);
    }
}
