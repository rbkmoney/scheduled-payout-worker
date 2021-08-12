package com.rbkmoney.scheduledpayoutworker.dao;

import com.rbkmoney.payouter.domain.tables.pojos.Refund;
import com.rbkmoney.scheduledpayoutworker.config.AbstractPostgreTestContainerConfig;
import com.rbkmoney.scheduledpayoutworker.exception.DaoException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

import static io.github.benas.randombeans.api.EnhancedRandom.random;
import static org.junit.jupiter.api.Assertions.assertEquals;

@TestPropertySource(properties = {"kafka.topics.invoice.enabled=false",
        "kafka.topics.party-management.enabled=false"})
class RefundDaoTest extends AbstractPostgreTestContainerConfig {

    @Autowired
    RefundDao refundDao;

    @Test
    void testSave() throws DaoException {
        Refund refund = random(Refund.class, "payoutId");
        refundDao.save(refund);

        Refund secondRefund = new Refund(refund);
        secondRefund.setId(null);
        refundDao.save(secondRefund);
    }

    @Test
    void testSaveOnlyNonNullValues() throws DaoException {
        Refund refund = random(Refund.class, "reason", "payoutId");
        refundDao.save(refund);
        assertEquals(refund, refundDao.get(refund.getInvoiceId(), refund.getPaymentId(), refund.getRefundId()));
    }

}
