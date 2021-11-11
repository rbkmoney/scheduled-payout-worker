package com.rbkmoney.scheduledpayoutworker.service;

import com.rbkmoney.scheduledpayoutworker.exception.NotFoundException;
import com.rbkmoney.scheduledpayoutworker.exception.StorageException;
import org.apache.thrift.TException;

import java.time.LocalDateTime;

public interface PayoutManagerService {

    /**
     * Create payout by partyId, shopId and toTime. fromTime = toTime - MAX_DAYS
     * @return payoutId if payout successfully created, null if payout amount = 0
     */
    String createPayoutByRange(
            String partyId,
            String shopId,
            LocalDateTime toTime
    ) throws NotFoundException, StorageException, TException;

}
