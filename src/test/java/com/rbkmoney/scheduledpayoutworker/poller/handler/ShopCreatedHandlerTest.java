package com.rbkmoney.scheduledpayoutworker.poller.handler;

import com.rbkmoney.damsel.domain.*;
import com.rbkmoney.damsel.payment_processing.*;
import com.rbkmoney.machinegun.eventsink.MachineEvent;
import com.rbkmoney.scheduledpayoutworker.dao.ShopMetaDao;
import com.rbkmoney.scheduledpayoutworker.poller.handler.impl.ShopCreatedHandler;
import com.rbkmoney.scheduledpayoutworker.service.PartyManagementService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.Map;

import static com.rbkmoney.scheduledpayoutworker.util.TestUtil.fillTBaseObject;
import static com.rbkmoney.scheduledpayoutworker.util.TestUtil.generateRandomStringId;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class ShopCreatedHandlerTest {

    @Mock
    private PartyManagementService partyManagementService;

    @Mock
    private ShopMetaDao shopMetaDao;

    private ShopCreatedHandler handler;

    private AutoCloseable mocks;

    private Object[] preparedMocks;

    @BeforeEach
    public void init() {
        mocks = MockitoAnnotations.openMocks(this);
        handler = new ShopCreatedHandler(partyManagementService, shopMetaDao);
        preparedMocks = new Object[] {partyManagementService, shopMetaDao};
    }

    @AfterEach
    public void clean() throws Exception {
        verifyNoMoreInteractions(preparedMocks);
        mocks.close();
    }

    @Test
    void accept() {
        assertTrue(handler.accept(preparePartyChange(), prepareEvent()));
    }

    @Test
    void handle() {
        PartyChange change = preparePartyChange();

        ShopEffectUnit effectUnit = change.getClaimCreated().getStatus()
                .getAccepted().getEffects().get(0)
                .getShopEffect();

        //prepare for hasPaymentInstitutionAccountPayTool check
        Shop shop = effectUnit.getEffect().getCreated();
        Contract contract = fillTBaseObject(new Contract(), Contract.class);
        contract.setId(shop.getContractId());
        PayoutTool payoutTool = fillTBaseObject(new PayoutTool(), PayoutTool.class);
        payoutTool.setId(shop.getPayoutToolId());
        contract.setPayoutTools(List.of(payoutTool));
        PayoutToolInfo toolInfo = fillTBaseObject(new PayoutToolInfo(), PayoutToolInfo.class);
        PaymentInstitutionAccount institutionAccount =
                fillTBaseObject(new PaymentInstitutionAccount(), PaymentInstitutionAccount.class);
        toolInfo.setPaymentInstitutionAccount(institutionAccount);
        payoutTool.setPayoutToolInfo(toolInfo);
        Party party = fillTBaseObject(new Party(), Party.class);
        party.setContracts(Map.of(shop.getContractId(), contract));

        MachineEvent event = prepareEvent();
        String partyId = event.getSourceId();
        String shopId = effectUnit.getShopId();
        party.setShops(Map.of(shopId, shop));

        when(partyManagementService.getParty(partyId)).thenReturn(party);
        handler.handle(change, event);
        verify(partyManagementService, times(1))
                .getParty(partyId);
        verify(shopMetaDao, times(1)).update(partyId, shopId, true);
    }


    private PartyChange preparePartyChange() {
        PartyChange change = fillTBaseObject(new PartyChange(), PartyChange.class);
        Claim claim = fillTBaseObject(new Claim(), Claim.class);
        change.setClaimCreated(claim);
        ClaimStatus status = fillTBaseObject(new ClaimStatus(), ClaimStatus.class);
        claim.setStatus(status);

        ClaimAccepted acceptedStatus = fillTBaseObject(new ClaimAccepted(), ClaimAccepted.class);
        status.setAccepted(acceptedStatus);

        ClaimEffect claimEffect = fillTBaseObject(new ClaimEffect(), ClaimEffect.class);
        ShopEffectUnit shopEffectUnit = fillTBaseObject(new ShopEffectUnit(), ShopEffectUnit.class);
        ShopEffect shopEffect = fillTBaseObject(new ShopEffect(), ShopEffect.class);
        shopEffectUnit.setEffect(shopEffect);
        claimEffect.setShopEffect(shopEffectUnit);
        Shop shop = fillTBaseObject(new Shop(), Shop.class);
        shop.setPayoutToolId(generateRandomStringId());
        shopEffect.setCreated(shop);
        List<ClaimEffect> claimEffects = List.of(claimEffect);
        acceptedStatus.setEffects(claimEffects);
        return change;
    }

    private MachineEvent prepareEvent() {
        MachineEvent event = fillTBaseObject(new MachineEvent(), MachineEvent.class);
        return event;
    }

}