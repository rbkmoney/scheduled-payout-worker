package com.rbkmoney.scheduledpayoutworker.poller.handler.impl;

import com.rbkmoney.damsel.domain.Cash;
import com.rbkmoney.damsel.payment_processing.InvoiceChange;
import com.rbkmoney.damsel.payment_processing.InvoicePaymentChange;
import com.rbkmoney.damsel.payment_processing.InvoicePaymentChargebackBodyChanged;
import com.rbkmoney.damsel.payment_processing.InvoicePaymentChargebackChange;
import com.rbkmoney.machinegun.eventsink.MachineEvent;
import com.rbkmoney.payouter.domain.tables.pojos.Chargeback;
import com.rbkmoney.scheduledpayoutworker.dao.ChargebackDao;
import com.rbkmoney.scheduledpayoutworker.dao.InvoiceDao;
import com.rbkmoney.scheduledpayoutworker.exception.NotFoundException;
import com.rbkmoney.scheduledpayoutworker.poller.handler.PaymentProcessingHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class InvoicePaymentChargebackBodyChangedHandler implements PaymentProcessingHandler {

    private final ChargebackDao chargebackDao;

    private final InvoiceDao invoiceDao;

    @Override
    public boolean accept(InvoiceChange invoiceChange, MachineEvent event) {
        return invoiceChange.isSetInvoicePaymentChange()
                && invoiceChange.getInvoicePaymentChange().getPayload()
                .isSetInvoicePaymentChargebackChange()
                && invoiceChange.getInvoicePaymentChange().getPayload()
                .getInvoicePaymentChargebackChange().getPayload()
                .isSetInvoicePaymentChargebackBodyChanged()
                && invoiceDao.get(event.getSourceId()) != null;
    }

    @Override
    public void handle(InvoiceChange invoiceChange, MachineEvent event) {
        String invoiceId = event.getSourceId();

        InvoicePaymentChange invoicePaymentChange = invoiceChange.getInvoicePaymentChange();
        String paymentId = invoicePaymentChange.getId();

        InvoicePaymentChargebackChange invoicePaymentChargebackChange = invoicePaymentChange.getPayload()
                .getInvoicePaymentChargebackChange();
        String chargebackId = invoicePaymentChargebackChange.getId();

        Chargeback chargeback = chargebackDao.get(invoiceId, paymentId, chargebackId);
        if (chargeback == null) {
            throw new NotFoundException(
                    String.format("Invoice chargeback not found, invoiceId='%s', paymentId='%s', chargebackId='%s'",
                            invoiceId, paymentId, chargebackId));
        }

        InvoicePaymentChargebackBodyChanged invoicePaymentChargebackBodyChanged =
                invoicePaymentChargebackChange.getPayload().getInvoicePaymentChargebackBodyChanged();

        Cash body = invoicePaymentChargebackBodyChanged.getBody();
        chargeback.setAmount(body.getAmount());
        chargeback.setCurrencyCode(body.getCurrency().getSymbolicCode());

        chargebackDao.save(chargeback);

        log.info("Chargeback body have been saved, invoiceId={}, paymentId={}, chargebackId={}",
                invoiceId, paymentId, chargebackId);
    }

}
