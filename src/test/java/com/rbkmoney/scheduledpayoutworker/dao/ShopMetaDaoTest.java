package com.rbkmoney.scheduledpayoutworker.dao;

import com.rbkmoney.payouter.domain.tables.pojos.ShopMeta;
import com.rbkmoney.scheduledpayoutworker.config.AbstractPostgreTestContainerConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.concurrent.CountDownLatch;

import static org.junit.jupiter.api.Assertions.*;

@TestPropertySource(properties = {"kafka.topics.invoice.enabled=false",
        "kafka.topics.party-management.enabled=false"})
class ShopMetaDaoTest extends AbstractPostgreTestContainerConfig {

    @Autowired
    TransactionTemplate transactionTemplate;
    @Autowired
    private ShopMetaDao shopMetaDao;

    @Test
    void testSaveAndGetShopMeta() {
        String partyId = "partyId";
        String shopId = "shopId";

        shopMetaDao.save(partyId, shopId, true);
        ShopMeta shopMeta = shopMetaDao.get(partyId, shopId);

        shopMetaDao.save(partyId, shopId, true);
        assertTrue(shopMeta.getWtime().isBefore(shopMetaDao.get(partyId, shopId).getWtime()));

        shopMetaDao.save(partyId, shopId, 1, 2, true);
        shopMeta = shopMetaDao.get(partyId, shopId);

        assertEquals(partyId, shopMeta.getPartyId());
        assertEquals(shopId, shopMeta.getShopId());
        assertEquals(1, (int) shopMeta.getCalendarId());
        assertEquals(2, (int) shopMeta.getSchedulerId());

        shopMetaDao.save(partyId, shopId, 2, 1, true);
        shopMeta = shopMetaDao.get(partyId, shopId);
        assertEquals(partyId, shopMeta.getPartyId());
        assertEquals(shopId, shopMeta.getShopId());
        assertEquals(2, (int) shopMeta.getCalendarId());
        assertEquals(1, (int) shopMeta.getSchedulerId());

        shopMetaDao.save("test2", "test2", 2, 1, true);
        List<ShopMeta> activeShops = shopMetaDao.getAllActiveShops();
        assertEquals(2, activeShops.size());

        List<ShopMeta> shopMetaList = shopMetaDao.getByCalendarAndSchedulerId(2, 1);
        assertEquals(2, shopMetaList.size());

        shopMetaDao.disableShop(partyId, shopId);
        shopMeta = shopMetaDao.get(partyId, shopId);
        assertEquals(partyId, shopMeta.getPartyId());
        assertEquals(shopId, shopMeta.getShopId());
        assertNull(shopMeta.getCalendarId());
        assertNull(shopMeta.getSchedulerId());

        assertEquals(1, shopMetaDao.getByCalendarAndSchedulerId(2, 1).size());
    }

    @Test
    void testGetExclusive() throws InterruptedException {
        String partyId = "partyId";
        String shopId = "shopId";
        long sleep = 1000L;

        shopMetaDao.save(partyId, shopId, true);

        final ShopMeta shopMeta = shopMetaDao.get(partyId, shopId);
        CountDownLatch latch = new CountDownLatch(1);
        new Thread(() ->
                transactionTemplate.execute((status) -> {
                    final ShopMeta exclusive = shopMetaDao.getExclusive(partyId, shopId);
                    latch.countDown();
                    try {
                        Thread.sleep(sleep);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    shopMetaDao.save(partyId, shopId, true);
                    return exclusive;
                })
        ).start();

        latch.await();

        long start = System.currentTimeMillis();
        ShopMeta newShopMeta = transactionTemplate.execute((status) -> shopMetaDao.getExclusive(partyId, shopId));
        assertTrue(sleep <= System.currentTimeMillis() - start);
        assertTrue(shopMeta.getWtime().isBefore(newShopMeta.getWtime()));

    }

}
