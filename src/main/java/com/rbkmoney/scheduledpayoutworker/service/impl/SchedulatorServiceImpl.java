package com.rbkmoney.scheduledpayoutworker.service.impl;

import com.rbkmoney.damsel.domain.BusinessScheduleRef;
import com.rbkmoney.damsel.domain.CalendarRef;
import com.rbkmoney.damsel.domain.PaymentInstitution;
import com.rbkmoney.damsel.domain.Shop;
import com.rbkmoney.damsel.schedule.*;
import com.rbkmoney.payouter.domain.tables.pojos.ShopMeta;
import com.rbkmoney.scheduledpayoutworker.dao.ShopMetaDao;
import com.rbkmoney.scheduledpayoutworker.exception.NotFoundException;
import com.rbkmoney.scheduledpayoutworker.model.ScheduledJobContext;
import com.rbkmoney.scheduledpayoutworker.serde.impl.ScheduledJobSerializer;
import com.rbkmoney.scheduledpayoutworker.service.DominantService;
import com.rbkmoney.scheduledpayoutworker.service.PartyManagementService;
import com.rbkmoney.scheduledpayoutworker.service.SchedulatorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.thrift.TException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.rbkmoney.scheduledpayoutworker.util.GenerateUtil.generatePayoutScheduleId;

@Slf4j
@Service
@RequiredArgsConstructor
public class SchedulatorServiceImpl implements SchedulatorService {

    private final DominantService dominantService;
    private final PartyManagementService partyManagementService;

    private final ShopMetaDao shopMetaDao;

    private final SchedulatorSrv.Iface schedulatorClient;

    private final ScheduledJobSerializer scheduledJobSerializer;

    @Value("${service.schedulator.callback-path}")
    private String callbackPath;

    @Override
    @Transactional
    public void registerJob(String partyId, String shopId, BusinessScheduleRef scheduleRef) {
        log.info("Trying to send create job request, partyId='{}', shopId='{}', scheduleRef='{}'",
                partyId, shopId, scheduleRef);

        Shop shop = partyManagementService.getShop(partyId, shopId);
        var paymentInstitutionRef = partyManagementService.getPaymentInstitutionRef(partyId, shop.getContractId());
        PaymentInstitution paymentInstitution = dominantService.getPaymentInstitution(paymentInstitutionRef);
        if (!paymentInstitution.isSetCalendar()) {
            throw new NotFoundException(String.format("Calendar not found, " +
                    "partyId='%s', shopId='%s', contractId='%s'", partyId, shop.getId(), shop.getContractId()));
        }

        try {
            deregisterJob(partyId, shopId);
        } catch (IllegalStateException e) {
            log.info("Job not found, so we couldn't deregister it");
        }

        CalendarRef calendarRef = paymentInstitution.getCalendar();

        shopMetaDao.save(partyId, shopId, calendarRef.getId(), scheduleRef.getId(), true);
        Schedule schedule = new Schedule();
        DominantBasedSchedule dominantBasedSchedule = new DominantBasedSchedule()
                .setBusinessScheduleRef(new BusinessScheduleRef().setId(scheduleRef.getId()))
                .setCalendarRef(calendarRef);
        schedule.setDominantSchedule(dominantBasedSchedule);

        String jobId = generatePayoutScheduleId(partyId, shopId, scheduleRef.getId());
        ScheduledJobContext context = new ScheduledJobContext();
        context.setPartyId(partyId);
        context.setShopId(shopId);
        context.setJobId(jobId);

        RegisterJobRequest registerJobRequest = new RegisterJobRequest()
                .setSchedule(schedule)
                .setExecutorServicePath(callbackPath)
                .setContext(scheduledJobSerializer.writeByte(context));

        try {
            schedulatorClient.registerJob(jobId, registerJobRequest);
        } catch (TException e) {
            throw new IllegalStateException(String.format("Register job '%s' failed", jobId), e);
        }

        log.info("Create job request have been successfully sent, " +
                        "partyId='{}', shopId='{}', calendarRef='{}', scheduleRef='{}'",
                partyId, shopId, calendarRef, scheduleRef);
    }

    @Override
    @Transactional
    public void deregisterJob(String partyId, String shopId) {
        ShopMeta shopMeta = shopMetaDao.get(partyId, shopId);
        log.info("Trying to deregister job, partyId='{}', shopId='{}'", partyId, shopId);
        shopMetaDao.disableShop(partyId, shopId);
        if (shopMeta.getSchedulerId() != null) {
            try {
                schedulatorClient.deregisterJob(String.valueOf(shopMeta.getSchedulerId()));
            } catch (TException e) {
                throw new IllegalStateException(
                        String.format("Deregister job '%s' failed", shopMeta.getSchedulerId()), e);
            }
        }
    }
}
