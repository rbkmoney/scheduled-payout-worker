package com.rbkmoney.scheduledpayoutworker.poller.handler.impl;

import com.rbkmoney.damsel.domain.Shop;
import com.rbkmoney.damsel.payment_processing.*;
import com.rbkmoney.machinegun.eventsink.MachineEvent;
import com.rbkmoney.scheduledpayoutworker.poller.handler.PartyManagementHandler;
import com.rbkmoney.scheduledpayoutworker.service.SchedulatorService;
import com.rbkmoney.scheduledpayoutworker.util.DamselUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@RequiredArgsConstructor
public class PartyClaimCreatedHandler implements PartyManagementHandler {

    private final SchedulatorService schedulatorService;

    @Override
    public boolean accept(PartyChange change) {
        return DamselUtil.isClaimAccepted(change);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handle(PartyChange change, MachineEvent event) {
        String partyId = event.getSourceId();
        List<ClaimEffect> claimEffects = DamselUtil.getClaimStatus(change)
                .getAccepted()
                .getEffects();

        for (ClaimEffect claimEffect : claimEffects) {
            if (claimEffect.isSetShopEffect()) {
                ShopEffectUnit shopEffectUnit = claimEffect.getShopEffect();
                String shopId = shopEffectUnit.getShopId();
                ShopEffect shopEffect = shopEffectUnit.getEffect();
                if (shopEffect.isSetPayoutScheduleChanged()) {
                    ScheduleChanged scheduleChanged = shopEffect.getPayoutScheduleChanged();
                    if (scheduleChanged.isSetSchedule()) {
                        schedulatorService.registerJob(partyId, shopId, scheduleChanged.getSchedule());
                    } else {
                        schedulatorService.deregisterJob(partyId, shopId);
                    }
                } else if (shopEffect.isSetCreated()) {
                    Shop shop = shopEffect.getCreated();
                    if (shop.isSetPayoutSchedule()) {
                        schedulatorService.registerJob(partyId, shopId, shop.getPayoutSchedule());
                    }
                }
            }
        }
    }

}
