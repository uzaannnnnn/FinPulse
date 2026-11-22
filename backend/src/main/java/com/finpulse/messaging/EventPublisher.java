package com.finpulse.messaging;

import com.finpulse.exception.MessagingUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Publishes domain events to RabbitMQ exchanges with transaction-commit awareness and failure isolation.
 */
@Component
public class EventPublisher {

    private static final Logger log = LoggerFactory.getLogger(EventPublisher.class);

    private final RabbitTemplate rabbitTemplate;

    public EventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    /**
     * Publishes {@link TransactionCreatedEvent} after DB commit. Broker failures are logged and swallowed
     * so core transaction persistence is never rolled back.
     */
    public void publishTransactionCreatedAfterCommit(TransactionCreatedEvent event) {
        if (event == null) {
            return;
        }

        Runnable publishTask = () -> {
            try {
                rabbitTemplate.convertAndSend(
                        RabbitMqConfig.EVENTS_EXCHANGE,
                        RabbitMqConfig.TRANSACTION_CREATED_ROUTING_KEY,
                        event
                );
            } catch (Exception ex) {
                log.warn(
                        "Failed publishing transaction.created event for transactionId={} userId={}: {}",
                        event.transactionId(),
                        event.userId(),
                        ex.getMessage()
                );
            }
        };

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    publishTask.run();
                }
            });
        } else {
            publishTask.run();
        }
    }

    /**
     * Publishes {@link ReportExportRequestedEvent} to RabbitMQ. Throws {@link MessagingUnavailableException}
     * if the broker is unreachable.
     */
    public void publishReportExportRequested(ReportExportRequestedEvent event) {
        try {
            rabbitTemplate.convertAndSend(
                    RabbitMqConfig.EVENTS_EXCHANGE,
                    RabbitMqConfig.REPORT_EXPORT_ROUTING_KEY,
                    event
            );
        } catch (AmqpException ex) {
            log.error(
                    "Failed publishing report.export event for reportId={} userId={}",
                    event.reportId(),
                    event.userId(),
                    ex
            );
            throw new MessagingUnavailableException(
                    "Layanan antrean laporan sedang tidak tersedia; silakan coba beberapa saat lagi",
                    ex
            );
        }
    }
}
