package com.rbkmoney.scheduledpayoutworker.service;

import com.rbkmoney.damsel.domain.*;
import com.rbkmoney.damsel.schedule.SchedulatorSrv;
import com.rbkmoney.payouter.domain.tables.pojos.ShopMeta;
import com.rbkmoney.scheduledpayoutworker.dao.ShopMetaDao;
import com.rbkmoney.scheduledpayoutworker.model.ScheduledJobContext;
import com.rbkmoney.scheduledpayoutworker.serde.impl.ScheduledJobSerializer;
import com.rbkmoney.scheduledpayoutworker.service.impl.SchedulatorServiceImpl;
import com.rbkmoney.scheduledpayoutworker.util.GenerateUtil;
import org.apache.thrift.TException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;
import org.springframework.test.util.ReflectionTestUtils;

import static com.rbkmoney.scheduledpayoutworker.util.TestUtil.*;
import static org.mockito.Mockito.*;

class SchedulatorServiceTest {

    private final String callbackPath = "test";

    @Mock
    private DominantService dominantService;
    @Mock
    private PartyManagementService partyManagementService;
    @Mock
    private ShopMetaDao shopMetaDao;
    @Mock
    private SchedulatorSrv.Iface schedulatorClient;
    @Mock
    private ScheduledJobSerializer scheduledJobSerializer;
    @Captor
    private ArgumentCaptor<ScheduledJobContext> scheduledJobContextCaptor;

    private SchedulatorService service;

    private AutoCloseable mocks;

    private Object[] preparedMocks;

    @BeforeEach
    public void init() {
        mocks = MockitoAnnotations.openMocks(this);
        preparedMocks = new Object[] {dominantService, partyManagementService, shopMetaDao,
                schedulatorClient, scheduledJobSerializer};
        service = new SchedulatorServiceImpl(dominantService, partyManagementService, shopMetaDao,
                schedulatorClient, scheduledJobSerializer);
        ReflectionTestUtils.setField(service, "callbackPath", callbackPath);
        Mockito.when(shopMetaDao.get(anyString(), anyString()))
                .thenReturn(new ShopMeta());
    }

    @AfterEach
    public void clean() throws Exception {
        verifyNoMoreInteractions(preparedMocks);
        mocks.close();
    }

    @Test
    void registerJob() throws TException {
        String partyId = generateRandomStringId();
        String shopId = generateRandomStringId();
        Shop shop = prepareShop(shopId);

        when(partyManagementService.getShop(partyId, shopId)).thenReturn(shop);

        PaymentInstitutionRef institutionRef = preparePaymentInstitutionRef();
        when(partyManagementService.getPaymentInstitutionRef(partyId, shop.getContractId())).thenReturn(institutionRef);
        PaymentInstitution paymentInstitution = preparePaymentInstitution();
        when(dominantService.getPaymentInstitution(institutionRef)).thenReturn(paymentInstitution);

        BusinessScheduleRef businessScheduleRef = prepareBusinessScheduleRef();
        ShopMeta shopMeta = new ShopMeta();
        shopMeta.setSchedulerId(generateRandomIntId());
        String payoutScheduleId = GenerateUtil.generatePayoutScheduleId(partyId, shopId, businessScheduleRef.getId());
        shopMeta.setPayoutScheduleId(payoutScheduleId);
        shopMeta.setHasPaymentInstitutionAccPayTool(true);

        when(shopMetaDao.get(partyId, shopId)).thenReturn(shopMeta);
        service.registerJob(partyId, shopId, businessScheduleRef);
        verify(partyManagementService, times(1)).getShop(partyId, shopId);
        verify(partyManagementService, times(1)).getPaymentInstitutionRef(partyId, shop.getContractId());
        verify(dominantService, times(1)).getPaymentInstitution(institutionRef);
        verify(shopMetaDao, times(1))
                .update(partyId, shopId, paymentInstitution.getCalendar().getId(), businessScheduleRef.getId(),
                        payoutScheduleId);
        verify(shopMetaDao, times(1)).get(partyId, shopId);
        verify(shopMetaDao, times(1)).disableShop(partyId, shopId);
        verify(schedulatorClient, times(1)).deregisterJob(shopMeta.getPayoutScheduleId());
        verify(scheduledJobSerializer, times(1)).writeByte(scheduledJobContextCaptor.capture());
        ScheduledJobContext context = scheduledJobContextCaptor.getValue();
        verify(schedulatorClient, times(1)).registerJob(eq(context.getJobId()), notNull());
    }

    @Test
    void deregisterJob() throws TException {
        String partyId = generateRandomStringId();
        String shopId = generateRandomStringId();
        ShopMeta shopMeta = new ShopMeta();
        shopMeta.setPayoutScheduleId(generateRandomStringId());
        when(shopMetaDao.get(partyId, shopId)).thenReturn(shopMeta);

        service.deregisterJob(partyId, shopId);

        verify(shopMetaDao, times(1)).get(partyId, shopId);
        verify(shopMetaDao, times(1)).disableShop(partyId, shopId);
        verify(schedulatorClient, times(1)).deregisterJob(shopMeta.getPayoutScheduleId());

    }

    private Shop prepareShop(String shopId) {
        Shop shop = fillTBaseObject(new Shop(), Shop.class);
        shop.setId(shopId);
        return shop;
    }

    private PaymentInstitutionRef preparePaymentInstitutionRef() {
        return fillTBaseObject(new PaymentInstitutionRef(), PaymentInstitutionRef.class);
    }

    private PaymentInstitution preparePaymentInstitution() {
        PaymentInstitution paymentInstitution = fillTBaseObject(new PaymentInstitution(), PaymentInstitution.class);
        CalendarRef calendarRef = fillTBaseObject(new CalendarRef(), CalendarRef.class);
        paymentInstitution.setCalendar(calendarRef);
        return paymentInstitution;
    }

    private BusinessScheduleRef prepareBusinessScheduleRef() {
        return fillTBaseObject(new BusinessScheduleRef(), BusinessScheduleRef.class);
    }
}