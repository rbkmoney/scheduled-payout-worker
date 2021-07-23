package com.rbkmoney.scheduledpayoutworker.integration;

import com.rbkmoney.damsel.domain.Party;
import com.rbkmoney.damsel.domain_config.RepositoryClientSrv;
import com.rbkmoney.damsel.domain_config.VersionedObject;
import com.rbkmoney.damsel.payment_processing.PartyManagementSrv;
import com.rbkmoney.damsel.schedule.*;
import com.rbkmoney.machinegun.eventsink.SinkEvent;
import com.rbkmoney.payout.manager.Payout;
import com.rbkmoney.payout.manager.PayoutManagementSrv;
import com.rbkmoney.payout.manager.PayoutParams;
import com.rbkmoney.payouter.domain.tables.Invoice;
import com.rbkmoney.payouter.domain.tables.ShopMeta;
import com.rbkmoney.scheduledpayoutworker.config.AbstractKafkaTestContainerConfig;
import com.rbkmoney.scheduledpayoutworker.dao.mapper.RecordRowMapper;
import com.rbkmoney.scheduledpayoutworker.integration.config.TestKafkaConfig;
import com.rbkmoney.woody.thrift.impl.http.THSpawnClientBuilder;
import lombok.SneakyThrows;
import org.awaitility.Awaitility;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.jdbc.JdbcTestUtils;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Callable;

import static com.rbkmoney.scheduledpayoutworker.util.TestUtil.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestKafkaConfig.class)
@ExtendWith(MockitoExtension.class)
class ScheduledPayoutWorkerIntegrationTest extends AbstractKafkaTestContainerConfig {

    private static final int MOCK_TIMEOUT_MILLIS = 10000;

    @MockBean
    public PartyManagementSrv.Iface partyManagementClient;

    @MockBean
    public RepositoryClientSrv.Iface dominantClient;

    @MockBean
    public SchedulatorSrv.Iface schedulatorClient;

    @MockBean
    public PayoutManagementSrv.Iface payoutManagerClient;

    @Autowired
    private KafkaTemplate<String, SinkEvent> producer;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Value("${kafka.topics.party-management.id}")
    private String partyTopic;

    @Value("${kafka.topics.invoice.id}")
    private String invoiceTopic;

    @LocalServerPort
    private int port;

    @Captor
    private ArgumentCaptor<String> scheduleIdCaptor;

    @Captor
    private ArgumentCaptor<RegisterJobRequest> jobRequestCaptor;

    @Captor
    private ArgumentCaptor<PayoutParams> payoutParamsCaptor;

    private AutoCloseable mocks;

    private Object[] preparedMocks;

    @BeforeEach
    public void init() {
        mocks = MockitoAnnotations.openMocks(this);
        preparedMocks = new Object[] {partyManagementClient, dominantClient, schedulatorClient, payoutManagerClient};
    }

    @AfterEach
    public void clean() throws Exception {
        verifyNoMoreInteractions(preparedMocks);
        mocks.close();
    }

    @Test
    @SneakyThrows
    void readInvoicesAndProcessSchedulatorJobSuccessfully() {

        String partyId = generateRandomStringId();
        String shopId = generateRandomStringId();
        Integer paymentInstitutionId = generateRandomIntId();

        Party party = createParty(partyId, shopId, paymentInstitutionId);
        VersionedObject versionedObject = createVersionedObject();

        //Create shop via ShopCreatedHandler

        when(partyManagementClient.checkout(any(), eq(partyId), any())).thenReturn(party);
        when(dominantClient.checkoutObject(any(), any())).thenReturn(versionedObject);

        producer.send(partyTopic, shopCreatedEvent(partyId, shopId));

        verify(partyManagementClient, timeout(MOCK_TIMEOUT_MILLIS).times(4))
                .checkout(any(), eq(partyId), any());
        verify(dominantClient, timeout(MOCK_TIMEOUT_MILLIS).times(1))
                .checkoutObject(any(),
                        argThat(reference -> paymentInstitutionId.equals(reference.getPaymentInstitution().getId())));
        verify(schedulatorClient, timeout(MOCK_TIMEOUT_MILLIS).times(1))
                .registerJob(scheduleIdCaptor.capture(), jobRequestCaptor.capture());

        List<com.rbkmoney.payouter.domain.tables.pojos.ShopMeta> shopMetas =
                jdbcTemplate
                        .query(
                                "SELECT party_id, shop_id, wtime, has_payment_institution_acc_pay_tool " +
                                        "FROM pt.shop_meta",
                                new RecordRowMapper<>(new ShopMeta(),
                                        com.rbkmoney.payouter.domain.tables.pojos.ShopMeta.class));
        assertEquals(1, shopMetas.size());
        var shopMeta = shopMetas.get(0);

        assertAll(
                () -> assertEquals(partyId, shopMeta.getPartyId()),
                () -> assertEquals(shopId, shopMeta.getShopId()),
                () -> assertTrue(shopMeta.getHasPaymentInstitutionAccPayTool()),
                () -> assertNotNull(shopMeta.getWtime())
        );


        //Send few invoices
        String invoiceId = generateRandomStringId();
        SinkEvent invoiceCreated = invoiceCreatedEvent(partyId, shopId, invoiceId);
        producer.send(invoiceTopic, invoiceCreated).completable().join();

        Awaitility.await()
                .atLeast(Duration.ofMillis(50))
                .atMost(Duration.ofSeconds(10))
                .with()
                .pollInterval(Duration.ofMillis(100))
                .until(recordsAppeared(Invoice.INVOICE.getSchema().getName() +
                        '.' +
                        Invoice.INVOICE.getName()));

        //Invoice should be saved to DB because it has party and shop which were already saved to DB
        List<com.rbkmoney.payouter.domain.tables.pojos.Invoice> invoices =
                jdbcTemplate.query("SELECT party_id, shop_id, created_at FROM pt.invoice",
                        new RecordRowMapper<>(new Invoice(),
                                com.rbkmoney.payouter.domain.tables.pojos.Invoice.class));
        assertEquals(1, invoices.size());
        var invoice = invoices.get(0);

        assertAll(
                () -> assertEquals(partyId, invoice.getPartyId()),
                () -> assertEquals(shopId, invoice.getShopId()),
                () -> assertNotNull(invoice.getCreatedAt())
        );

        //Invoice with non-existing partyId and shopId must not be saved
        String unexpectedPartyId = "test";
        String unexpectedShopId = "test";
        String unexpectedInvoiceId = "test";
        producer.send(invoiceTopic, invoiceCreatedEvent(unexpectedPartyId, unexpectedShopId, unexpectedInvoiceId));

        invoices =
                jdbcTemplate.query("SELECT party_id, shop_id, created_at FROM pt.invoice where party_id = ?",
                        new RecordRowMapper<>(new Invoice(),
                                com.rbkmoney.payouter.domain.tables.pojos.Invoice.class),
                        unexpectedPartyId);
        assertTrue(invoices.isEmpty());

        //Create payment
        String paymentTimestamp = "2021-07-01T10:15:30Z";
        long amount = 100L;
        String paymentId = generateRandomStringId();
        producer.send(invoiceTopic, paymentCreatedEvent(paymentId, invoiceId, paymentTimestamp, amount));
        //Capture payment
        producer.send(invoiceTopic, paymentCapturedEvent(invoiceId, paymentId, paymentTimestamp));

        //Check triggered job processing
        ScheduledJobExecutorSrv.Iface client = new THSpawnClientBuilder()
                .withNetworkTimeout(0)
                .withAddress(new URI("http://localhost:" + port + "/v1/schedulator"))
                .build(ScheduledJobExecutorSrv.Iface.class);

        RegisterJobRequest registerJobRequest = jobRequestCaptor.getValue();
        ContextValidationResponse validationResponse =
                client.validateExecutionContext(registerJobRequest.bufferForContext());

        assertTrue(validationResponse.getResponseStatus().isSetSuccess());

        when(payoutManagerClient.createPayout(any())).thenReturn(fillTBaseObject(new Payout(), Payout.class));

        String schedulatorExecutionTimestamp = "2021-07-02T10:15:30Z";

        ExecuteJobRequest executeJobRequest = fillTBaseObject(new ExecuteJobRequest(), ExecuteJobRequest.class);
        executeJobRequest.setServiceExecutionContext(registerJobRequest.bufferForContext());
        ScheduledJobContext context = fillTBaseObject(new ScheduledJobContext(), ScheduledJobContext.class);
        context.setNextCronTime(schedulatorExecutionTimestamp);
        executeJobRequest.setScheduledJobContext(context);
        assertEquals(registerJobRequest.bufferForContext(), client.executeJob(executeJobRequest));

        verify(partyManagementClient, times(5)).checkout(any(), eq(partyId), any());
        verify(payoutManagerClient, times(1)).createPayout(payoutParamsCaptor.capture());

        PayoutParams payoutParams = payoutParamsCaptor.getValue();

        assertAll(
                () -> assertEquals(party.getShops().get(shopId).getAccount().getCurrency().getSymbolicCode(),
                        payoutParams.getCash().getCurrency().getSymbolicCode()),
                () -> assertEquals(amount, payoutParams.getCash().getAmount()),
                () -> assertEquals(partyId, payoutParams.getShopParams().getPartyId()),
                () -> assertEquals(shopId, payoutParams.getShopParams().getShopId())
        );

    }

    private Callable<Boolean> recordsAppeared(String tableName) {
        return () -> JdbcTestUtils.countRowsInTable(jdbcTemplate, tableName) > 0;
    }


}
