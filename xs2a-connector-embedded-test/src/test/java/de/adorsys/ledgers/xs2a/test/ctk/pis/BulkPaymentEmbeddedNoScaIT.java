package de.adorsys.ledgers.xs2a.test.ctk.pis;

import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import de.adorsys.ledgers.xs2a.api.client.PaymentApiClient;
import de.adorsys.ledgers.xs2a.test.ctk.StarterApplication;
import de.adorsys.psd2.model.*;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringBootTest(classes = StarterApplication.class)
public class BulkPaymentEmbeddedNoScaIT {
    private final YAMLMapper ymlMapper = new YAMLMapper();
    private final String paymentService = "payments";
    private final String paymentProduct = "sepa-credit-transfers";

    @Autowired
    private PaymentApiClient paymentApi;

    private PaymentExecutionHelper paymentInitService;

    @Before
    public void beforeClass() {
        PaymentCase paymentCase = LoadPayment.loadPayment(BulkPaymentEmbeddedNoScaIT.class, "BulkPaymentEmbeddedNoScaIT.yml", ymlMapper);
        paymentInitService = new PaymentExecutionHelper(paymentApi, paymentCase, paymentService, paymentProduct);
    }

    @Test
    public void test_create_payment() {
        // Initiate Payment
        PaymentInitationRequestResponse201 initiatedPayment = paymentInitService.initiatePayment();

        // Login User
        UpdatePsuAuthenticationResponse psuAuthenticationResponse = paymentInitService.login(initiatedPayment);
        Assert.assertNotNull(psuAuthenticationResponse);
        ScaStatus scaStatus = psuAuthenticationResponse.getScaStatus();
        Assert.assertNotNull(scaStatus);
        Assert.assertEquals(ScaStatus.FINALISED, scaStatus);


        PaymentInitiationStatusResponse200Json paymentStatus = paymentInitService.loadPaymentStatus(psuAuthenticationResponse);
        Assert.assertNotNull(paymentStatus);
        TransactionStatus transactionStatus = paymentStatus.getTransactionStatus();
        Assert.assertNotNull(transactionStatus);
        Assert.assertEquals(TransactionStatus.ACSP, transactionStatus);

    }
}