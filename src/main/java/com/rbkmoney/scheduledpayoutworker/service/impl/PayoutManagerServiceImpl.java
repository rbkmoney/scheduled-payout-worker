package com.rbkmoney.scheduledpayoutworker.service.impl;

import com.rbkmoney.damsel.domain.Shop;
import com.rbkmoney.payout.manager.PayoutManagementSrv;
import com.rbkmoney.payout.manager.PayoutParams;
import com.rbkmoney.payout.manager.ShopParams;
import com.rbkmoney.payout.manager.domain.Cash;
import com.rbkmoney.payout.manager.domain.CurrencyRef;
import com.rbkmoney.scheduledpayoutworker.dao.*;
import com.rbkmoney.scheduledpayoutworker.exception.DaoException;
import com.rbkmoney.scheduledpayoutworker.exception.InvalidStateException;
import com.rbkmoney.scheduledpayoutworker.exception.NotFoundException;
import com.rbkmoney.scheduledpayoutworker.exception.StorageException;
import com.rbkmoney.scheduledpayoutworker.service.PartyManagementService;
import com.rbkmoney.scheduledpayoutworker.service.PayoutManagerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.thrift.TException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PayoutManagerServiceImpl implements PayoutManagerService {

    public static final int MAX_DAYS = 7;
    private final PaymentDao paymentDao;
    private final RefundDao refundDao;
    private final AdjustmentDao adjustmentDao;
    private final ChargebackDao chargebackDao;
    private final PayoutDao payoutDao;

    private final PayoutManagementSrv.Iface payoutManagerClient;
    private final PartyManagementService partyManagementService;

    /**
     * Create payout by partyId, shopId and toTime. fromTime = toTime - MAX_DAYS
     * @return payoutId if payout successfully created, null if payout amount = 0
     */
    @Override
    @Transactional
    public String createPayoutByRange(String partyId, String shopId, LocalDateTime toTime)
            throws NotFoundException, StorageException, TException {

        Shop shop = partyManagementService.getShop(partyId, shopId);

        if (shop.getBlocking().isSetBlocked()) {
            throw new InvalidStateException(
                    String.format("Party or shop blocked for payouts, partyId='%s', shopId='%s'", partyId, shopId)
            );
        }

        String payoutId = UUID.randomUUID().toString();
        LocalDateTime fromTime = toTime.minusDays(MAX_DAYS);
        includeUnpaid(payoutId, partyId, shopId, fromTime, toTime);

        long amount = calculateAvailableAmount(payoutId);
        if (amount == 0) {
            return null;
        }

        String symbolicCode = shop.getAccount().getCurrency().getSymbolicCode();
        CurrencyRef currency = new CurrencyRef().setSymbolicCode(symbolicCode);
        Cash cash = new Cash().setAmount(amount).setCurrency(currency);
        ShopParams shopParams = new ShopParams().setPartyId(partyId).setShopId(shopId);
        PayoutParams payoutParams = new PayoutParams(shopParams, cash);
        payoutParams.setPayoutId(payoutId);

        payoutManagerClient.createPayout(payoutParams);
        return payoutId;
    }

    private void includeUnpaid(String payoutId, String partyId, String shopId, LocalDateTime fromTime,
                               LocalDateTime toTime)
            throws StorageException {
        log.info("Trying to include operations in payout," +
                        " payoutId='{}', partyId='{}', shopId='{}', fromTime='{}', toTime='{}'",
                payoutId, partyId, shopId, fromTime, toTime);
        try {
            int paymentCount = paymentDao.includeUnpaid(payoutId, partyId, shopId, fromTime, toTime);
            int refundCount = refundDao.includeUnpaid(payoutId, partyId, shopId, fromTime, toTime);
            int adjustmentCount = adjustmentDao.includeUnpaid(payoutId, partyId, shopId, fromTime, toTime);
            int chargebackCount = chargebackDao.includeUnpaid(payoutId, partyId, shopId, fromTime, toTime);
            log.info("Operations have been included in payout, payoutId='{}' (paymentCount='{}', refundCount='{}', " +
                            "adjustmentCount='{}', chargebackCount='{}')",
                    payoutId, paymentCount, refundCount, adjustmentCount, chargebackCount);
        } catch (DaoException ex) {
            throw new StorageException(
                    String.format("Failed to include operations in payout, payoutId='%s'", payoutId), ex);
        }
    }

    private long calculateAvailableAmount(String payoutId) {
        try {
            long amount = payoutDao.getAvailableAmount(payoutId);
            log.info("Available amount have been calculated, payoutId='{}', availableAmount={}", payoutId, amount);
            return amount;
        } catch (DaoException ex) {
            throw new StorageException(ex);
        }
    }

}
