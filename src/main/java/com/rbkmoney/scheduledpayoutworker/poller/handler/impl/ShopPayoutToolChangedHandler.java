package com.rbkmoney.scheduledpayoutworker.poller.handler.impl;

import com.rbkmoney.damsel.domain.Party;
import com.rbkmoney.damsel.domain.Shop;
import com.rbkmoney.damsel.payment_processing.ClaimEffect;
import com.rbkmoney.damsel.payment_processing.PartyChange;
import com.rbkmoney.machinegun.eventsink.MachineEvent;
import com.rbkmoney.scheduledpayoutworker.dao.ShopMetaDao;
import com.rbkmoney.scheduledpayoutworker.poller.handler.PartyManagementHandler;
import com.rbkmoney.scheduledpayoutworker.service.PartyManagementService;
import com.rbkmoney.scheduledpayoutworker.util.DamselUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class ShopPayoutToolChangedHandler implements PartyManagementHandler {

    private final PartyManagementService partyManagementService;
    private final ShopMetaDao shopMetaDao;

    @Override
    public boolean accept(PartyChange partyChange) {
        return DamselUtil.isClaimAccepted(partyChange);
    }

    @Override
    public void handle(PartyChange change, MachineEvent event) {
        List<ClaimEffect> claimEffects = DamselUtil.getClaimStatus(change).getAccepted().getEffects();
        for (ClaimEffect claimEffect : claimEffects) {
            if (claimEffect.isSetShopEffect() && claimEffect.getShopEffect().getEffect().isSetPayoutToolChanged()) {
                handleEvent(event, claimEffect);
            }
        }
    }

    private void handleEvent(MachineEvent event, ClaimEffect claimEffect) {

        String shopId = claimEffect.getShopEffect().getShopId();
        String partyId = event.getSourceId();

        String changedPayoutToolId = claimEffect.getShopEffect().getEffect().getPayoutToolChanged();

        Party party = partyManagementService.getParty(partyId);
        Shop shop = party.getShops().get(shopId);
        boolean hasPaymentInstitutionAccPayTool =
                DamselUtil.hasPaymentInstitutionAccountPayTool(party, shop.getContractId(), changedPayoutToolId);

        if (hasPaymentInstitutionAccPayTool || shopMetaDao.get(partyId, shopId) != null) {
            shopMetaDao.save(partyId, shopId, hasPaymentInstitutionAccPayTool);
            log.info("Shop have been saved, partyId={}, shopId={}", partyId, shopId);
        }

    }
}
