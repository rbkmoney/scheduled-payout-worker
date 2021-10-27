package com.rbkmoney.scheduledpayoutworker.poller.handler.impl;

import com.rbkmoney.damsel.domain.Party;
import com.rbkmoney.damsel.payment_processing.ClaimEffect;
import com.rbkmoney.damsel.payment_processing.ContractEffectUnit;
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
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class ContractPayoutToolInfoChangedHandler implements PartyManagementHandler {

    private final PartyManagementService partyManagementService;
    private final ShopMetaDao shopMetaDao;

    @Override
    public boolean accept(PartyChange partyChange, MachineEvent event) {
        return DamselUtil.isClaimAccepted(partyChange);
    }

    @Override
    public void handle(PartyChange change, MachineEvent event) {
        List<ClaimEffect> claimEffects = DamselUtil.getClaimStatus(change).getAccepted().getEffects();
        for (ClaimEffect claimEffect : claimEffects) {
            if (claimEffect.isSetContractEffect()
                    && claimEffect.getContractEffect().getEffect().isSetPayoutToolInfoChanged()) {
                handleEvent(event, claimEffect);
            }
        }
    }

    private void handleEvent(MachineEvent event, ClaimEffect claimEffect) {
        ContractEffectUnit contractEffect = claimEffect.getContractEffect();
        String partyId = event.getSourceId();
        Party party = partyManagementService.getParty(partyId);
        String contractId = contractEffect.getContractId();
        String shopId = getShopId(contractId, party);
        boolean hasPaymentInstitutionAccPayTool =
                contractEffect.getEffect().getPayoutToolInfoChanged().getInfo().isSetPaymentInstitutionAccount();
        if (hasPaymentInstitutionAccPayTool || shopMetaDao.get(partyId, shopId) != null) {
            shopMetaDao.save(partyId, shopId, hasPaymentInstitutionAccPayTool);
            log.info("Shop have been saved, partyId={}, shopId={}", partyId, shopId);
        }
    }

    private String getShopId(String contractId, Party party) {
        return party.getShops().entrySet().stream()
                .filter(e -> e.getValue().getContractId().equals(contractId))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(null);
    }
}
