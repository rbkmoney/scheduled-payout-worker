package com.rbkmoney.scheduledpayoutworker.poller.listener;

import com.rbkmoney.damsel.payment_processing.EventPayload;
import com.rbkmoney.kafka.common.util.LogUtil;
import com.rbkmoney.machinegun.eventsink.MachineEvent;
import com.rbkmoney.machinegun.eventsink.SinkEvent;
import com.rbkmoney.scheduledpayoutworker.service.PaymentProcessingEventService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.core.convert.ConversionService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;

import java.util.List;

@Slf4j
@RequiredArgsConstructor
public class InvoicingKafkaListener {

    private final PaymentProcessingEventService paymentProcessingEventService;
    private final ConversionService converter;

    @KafkaListener(topics = "${kafka.topics.invoice.id}", containerFactory = "invContainerFactory")
    public void handle(List<ConsumerRecord<String, SinkEvent>> messages, Acknowledgment ack) {
        log.info("Got invoice machineEvent batch with size: {}", messages.size());
        for (ConsumerRecord<String, SinkEvent> sinkEvent : messages) {
            if (sinkEvent != null && sinkEvent.value().isSetEvent()) {
                MachineEvent event = sinkEvent.value().getEvent();
                log.debug("Reading sinkEvent, sourceId: {}, sequenceId: {}", event.getSourceId(), event.getEventId());
                EventPayload payload = converter.convert(event, EventPayload.class);
                if (payload != null && payload.isSetInvoiceChanges()) {
                    paymentProcessingEventService.processEvent(event, payload);
                }
            }
        }
        ack.acknowledge();
        log.info("Batch invoice has been committed, size={}, {}", messages.size(),
                LogUtil.toSummaryStringWithSinkEventValues(messages));
    }

}
