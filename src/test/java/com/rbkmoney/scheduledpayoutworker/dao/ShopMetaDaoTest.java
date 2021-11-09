package com.rbkmoney.scheduledpayoutworker.dao;

import com.rbkmoney.payouter.domain.tables.pojos.ShopMeta;
import com.rbkmoney.scheduledpayoutworker.config.AbstractPostgreTestContainerConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.*;

@TestPropertySource(properties = {"kafka.topics.invoice.enabled=false",
        "kafka.topics.party-management.enabled=false"})
class ShopMetaDaoTest extends AbstractPostgreTestContainerConfig {

    @Autowired
    private ShopMetaDao shopMetaDao;

    @Test
    void testSaveAndGetShopMeta() {
        String partyId = "partyId";
        String shopId = "shopId";

        shopMetaDao.update(partyId, shopId, true);
        ShopMeta shopMeta = shopMetaDao.get(partyId, shopId);

        shopMetaDao.update(partyId, shopId, true);
        assertTrue(shopMeta.getWtime().isBefore(shopMetaDao.get(partyId, shopId).getWtime()));

        shopMetaDao.update(partyId, shopId, 1, 2, "zzz");
        shopMeta = shopMetaDao.get(partyId, shopId);

        assertEquals(partyId, shopMeta.getPartyId());
        assertEquals(shopId, shopMeta.getShopId());
        assertEquals(1, (int) shopMeta.getCalendarId());
        assertEquals(2, (int) shopMeta.getSchedulerId());
        assertEquals("zzz", shopMeta.getPayoutScheduleId());

        shopMetaDao.update(partyId, shopId, 2, 1, "zzz");
        shopMeta = shopMetaDao.get(partyId, shopId);
        assertEquals(partyId, shopMeta.getPartyId());
        assertEquals(shopId, shopMeta.getShopId());
        assertEquals(2, (int) shopMeta.getCalendarId());
        assertEquals(1, (int) shopMeta.getSchedulerId());

        shopMetaDao.disableShop(partyId, shopId);
        shopMeta = shopMetaDao.get(partyId, shopId);
        assertEquals(partyId, shopMeta.getPartyId());
        assertEquals(shopId, shopMeta.getShopId());
        assertNull(shopMeta.getCalendarId());
        assertNull(shopMeta.getSchedulerId());
    }
}
