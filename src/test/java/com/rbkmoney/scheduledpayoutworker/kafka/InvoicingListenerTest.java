package com.rbkmoney.scheduledpayoutworker.kafka;

import com.rbkmoney.damsel.payment_processing.Event;
import com.rbkmoney.damsel.payment_processing.EventPayload;
import com.rbkmoney.damsel.payment_processing.InvoiceChange;
import com.rbkmoney.machinegun.eventsink.MachineEvent;
import com.rbkmoney.machinegun.eventsink.SinkEvent;
import com.rbkmoney.scheduledpayoutworker.converter.SourceEventConverter;
import com.rbkmoney.scheduledpayoutworker.poller.listener.InvoicingKafkaListener;
import com.rbkmoney.scheduledpayoutworker.service.PaymentProcessingEventService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.kafka.support.Acknowledgment;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;

public class InvoicingListenerTest {

    @Mock
    private PaymentProcessingEventService paymentProcessingEventService;
    @Mock
    private SourceEventConverter parser;
    @Mock
    private Acknowledgment ack;

    private InvoicingKafkaListener listener;

    private AutoCloseable mocks;

    @BeforeEach
    public void init() {
        mocks = MockitoAnnotations.openMocks(this);
        listener = new InvoicingKafkaListener(paymentProcessingEventService, parser);
    }

    @AfterEach
    public void clean() throws Exception {
        mocks.close();
    }

    @Test
    public void listenNonInvoiceChanges() {

        MachineEvent message = new MachineEvent();
        Event event = new Event();
        EventPayload payload = new EventPayload();
        payload.setCustomerChanges(List.of());
        event.setPayload(payload);
        Mockito.when(parser.convert(message)).thenReturn(payload);

        SinkEvent sinkEvent = new SinkEvent();
        sinkEvent.setEvent(message);

        listener.handle(List.of(
                new ConsumerRecord<>("Test", 0, 0, "", sinkEvent)
        ), ack);

        Mockito.verify(paymentProcessingEventService, Mockito.times(0)).processEvent(any(), any());
        Mockito.verify(ack, Mockito.times(1)).acknowledge();
    }

    @Test
    public void listenEmptyException() {
        MachineEvent message = new MachineEvent();

        SinkEvent sinkEvent = new SinkEvent();
        sinkEvent.setEvent(message);

        Mockito.when(parser.convert(message)).thenThrow(new RuntimeException());

        assertThrows(RuntimeException.class, () -> listener.handle(List.of(
                new ConsumerRecord<>("Test", 0, 0, "", sinkEvent)
        ), ack));

        Mockito.verify(ack, Mockito.times(0)).acknowledge();
    }

    @Test
    public void listenChanges() {
        Event event = new Event();
        EventPayload payload = new EventPayload();
        ArrayList<InvoiceChange> invoiceChanges = new ArrayList<>();
        invoiceChanges.add(new InvoiceChange());
        payload.setInvoiceChanges(invoiceChanges);
        event.setPayload(payload);
        MachineEvent message = new MachineEvent();
        Mockito.when(parser.convert(message)).thenReturn(payload);

        SinkEvent sinkEvent = new SinkEvent();
        sinkEvent.setEvent(message);

        listener.handle(List.of(
                new ConsumerRecord<>("Test", 0, 0, "", sinkEvent)
        ), ack);

        Mockito.verify(paymentProcessingEventService, Mockito.times(1)).processEvent(any(), any());
        Mockito.verify(ack, Mockito.times(1)).acknowledge();
    }

}