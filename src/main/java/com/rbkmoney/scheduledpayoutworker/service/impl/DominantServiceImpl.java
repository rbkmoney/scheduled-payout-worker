package com.rbkmoney.scheduledpayoutworker.service.impl;

import com.rbkmoney.damsel.domain.*;
import com.rbkmoney.damsel.domain_config.Reference;
import com.rbkmoney.damsel.domain_config.*;
import com.rbkmoney.scheduledpayoutworker.exception.NotFoundException;
import com.rbkmoney.scheduledpayoutworker.service.DominantService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.thrift.TException;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class DominantServiceImpl implements DominantService {

    private final RepositoryClientSrv.Iface dominantClient;

    private final RetryTemplate retryTemplate;

    @Override
    public BusinessSchedule getBusinessSchedule(BusinessScheduleRef scheduleRef) throws NotFoundException {
        return getBusinessSchedule(scheduleRef, Reference.head(new Head()));
    }

    private BusinessSchedule getBusinessSchedule(BusinessScheduleRef scheduleRef, Reference revisionReference)
            throws NotFoundException {
        log.info("Trying to get schedule, scheduleRef='{}', revisionReference='{}'", scheduleRef, revisionReference);
        try {
            com.rbkmoney.damsel.domain.Reference reference = new com.rbkmoney.damsel.domain.Reference();
            reference.setBusinessSchedule(scheduleRef);
            VersionedObject versionedObject = checkoutObject(revisionReference, reference);
            BusinessSchedule schedule = versionedObject.getObject().getBusinessSchedule().getData();
            log.info("Schedule has been found, scheduleRef='{}', revisionReference='{}', schedule='{}'",
                    scheduleRef, revisionReference, schedule);
            return schedule;
        } catch (VersionNotFound | ObjectNotFound ex) {
            throw new NotFoundException(String.format("Version not found, scheduleRef='%s', revisionReference='%s'",
                    scheduleRef, revisionReference), ex);
        } catch (TException ex) {
            throw new RuntimeException(String.format("Failed to get schedule, scheduleRef='%s', revisionReference='%s'",
                    scheduleRef, revisionReference), ex);
        }
    }

    @Override
    public PaymentInstitution getPaymentInstitution(PaymentInstitutionRef paymentInstitutionRef)
            throws NotFoundException {
        return getPaymentInstitution(paymentInstitutionRef, Reference.head(new Head()));
    }

    private PaymentInstitution getPaymentInstitution(
            PaymentInstitutionRef paymentInstitutionRef,
            Reference revisionReference) throws NotFoundException {
        log.info("Trying to get payment institution, paymentInstitutionRef='{}', revisionReference='{}'",
                paymentInstitutionRef, revisionReference);
        try {
            com.rbkmoney.damsel.domain.Reference reference = new com.rbkmoney.damsel.domain.Reference();
            reference.setPaymentInstitution(paymentInstitutionRef);
            VersionedObject versionedObject = checkoutObject(revisionReference, reference);
            PaymentInstitution paymentInstitution = versionedObject.getObject().getPaymentInstitution().getData();
            log.info("Payment institution has been found, PaymentInstitutionRef='{}', revisionReference='{}', " +
                    "paymentInstitution='{}'", paymentInstitutionRef, revisionReference, paymentInstitution);
            return paymentInstitution;
        } catch (VersionNotFound | ObjectNotFound ex) {
            throw new NotFoundException(
                    String.format("Version not found, paymentInstitutionRef='%s', revisionReference='%s'",
                            paymentInstitutionRef, revisionReference), ex);
        } catch (TException ex) {
            throw new RuntimeException(
                    String.format("Failed to get payment institution, paymentInstitutionRef='%s', " +
                            "revisionReference='%s'", paymentInstitutionRef, revisionReference), ex);
        }
    }

    @Override
    public Calendar getCalendar(CalendarRef calendarRef) throws NotFoundException {
        return getCalendar(calendarRef, Reference.head(new Head()));
    }

    private Calendar getCalendar(CalendarRef calendarRef, Reference revisionReference) throws NotFoundException {
        log.info("Trying to get calendar, calendarRef='{}', revisionReference='{}'", calendarRef, revisionReference);
        try {
            com.rbkmoney.damsel.domain.Reference reference = new com.rbkmoney.damsel.domain.Reference();
            reference.setCalendar(calendarRef);
            VersionedObject versionedObject = checkoutObject(revisionReference, reference);
            Calendar calendar = versionedObject.getObject().getCalendar().getData();
            log.info("Calendar has been found, calendarRef='{}', revisionReference='{}', calendar='{}'",
                    calendarRef, revisionReference, calendar);
            return calendar;
        } catch (VersionNotFound | ObjectNotFound ex) {
            throw new NotFoundException(
                    String.format("Version not found, calendarRef='%s', revisionReference='%s'",
                            calendarRef, revisionReference), ex);
        } catch (TException ex) {
            throw new RuntimeException(
                    String.format("Failed to get calendar, calendarRef='%s', revisionReference='%s'",
                            calendarRef, revisionReference), ex);
        }
    }

    private VersionedObject checkoutObject(Reference revisionReference, com.rbkmoney.damsel.domain.Reference reference)
            throws TException {
        return retryTemplate.execute(
                context -> dominantClient.checkoutObject(revisionReference, reference)
        );
    }
}
