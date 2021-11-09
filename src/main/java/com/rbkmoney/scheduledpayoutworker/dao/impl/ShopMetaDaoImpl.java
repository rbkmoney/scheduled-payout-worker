package com.rbkmoney.scheduledpayoutworker.dao.impl;

import com.rbkmoney.payouter.domain.tables.pojos.ShopMeta;
import com.rbkmoney.scheduledpayoutworker.dao.ShopMetaDao;
import com.rbkmoney.scheduledpayoutworker.dao.mapper.RecordRowMapper;
import com.rbkmoney.scheduledpayoutworker.exception.DaoException;
import org.jooq.Query;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static com.rbkmoney.payouter.domain.Tables.SHOP_META;

@Component
public class ShopMetaDaoImpl extends AbstractGenericDao implements ShopMetaDao {

    private final RowMapper<ShopMeta> shopMetaRowMapper;

    @Autowired
    public ShopMetaDaoImpl(DataSource dataSource) {
        super(dataSource);
        shopMetaRowMapper = new RecordRowMapper<>(SHOP_META, ShopMeta.class);
    }

    @Override
    public void update(String partyId, String shopId, boolean hasPaymentInstitutionAccPayTool) throws DaoException {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);

        Query query = getDslContext().insertInto(SHOP_META)
                .set(SHOP_META.PARTY_ID, partyId)
                .set(SHOP_META.SHOP_ID, shopId)
                .set(SHOP_META.WTIME, now)
                .set(SHOP_META.HAS_PAYMENT_INSTITUTION_ACC_PAY_TOOL, hasPaymentInstitutionAccPayTool)
                .onDuplicateKeyUpdate()
                .set(SHOP_META.WTIME, now)
                .set(SHOP_META.HAS_PAYMENT_INSTITUTION_ACC_PAY_TOOL, hasPaymentInstitutionAccPayTool);

        executeOne(query);
    }

    @Override
    public void update(String partyId, String shopId, int calendarId, int schedulerId,
                       String payoutScheduleId) throws DaoException {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);

        Query query = getDslContext().update(SHOP_META)
                .set(SHOP_META.CALENDAR_ID, calendarId)
                .set(SHOP_META.SCHEDULER_ID, schedulerId)
                .set(SHOP_META.WTIME, now)
                .set(SHOP_META.PAYOUT_SCHEDULE_ID, payoutScheduleId)
                .where(SHOP_META.PARTY_ID.eq(partyId)
                        .and(SHOP_META.SHOP_ID.eq(shopId)));

        executeOne(query);
    }

    @Override
    public ShopMeta get(String partyId, String shopId) throws DaoException {
        Query query = getDslContext().selectFrom(SHOP_META)
                .where(SHOP_META.PARTY_ID.eq(partyId)
                        .and(SHOP_META.SHOP_ID.eq(shopId)));
        return fetchOne(query, shopMetaRowMapper);
    }

    @Override
    public void disableShop(String partyId, String shopId) throws DaoException {
        Query query = getDslContext().update(SHOP_META)
                .setNull(SHOP_META.CALENDAR_ID)
                .setNull(SHOP_META.SCHEDULER_ID)
                .setNull(SHOP_META.PAYOUT_SCHEDULE_ID)
                .where(SHOP_META.PARTY_ID.eq(partyId)
                        .and(SHOP_META.SHOP_ID.eq(shopId)));

        executeOne(query);
    }

}
