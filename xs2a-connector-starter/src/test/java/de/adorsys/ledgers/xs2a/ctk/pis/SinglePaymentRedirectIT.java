package de.adorsys.ledgers.xs2a.ctk.pis;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.UUID;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import de.adorsys.ledgers.xs2a.ctk.core.AbstractITTest;
import de.adorsys.psd2.client.ApiException;
import de.adorsys.psd2.client.api.PaymentInitiationServicePisApi;
import de.adorsys.psd2.client.model.PaymentInitationRequestResponse201;

@RunWith(SpringJUnit4ClassRunner.class)
public class SinglePaymentRedirectIT extends AbstractITTest {
	ObjectMapper ymlMapper = new ObjectMapper(new YAMLFactory());

	@SuppressWarnings("unchecked")
	@Test
	public void test_create_payment() throws JsonParseException, JsonMappingException, IOException {
        PaymentInitiationServicePisApi pis = new PaymentInitiationServicePisApi(createApiClient());
        UUID xRequestId = UUID.randomUUID();
        String tppProcessId = UUID.randomUUID().toString();
		String tpPRedirectURI = getTppOkRedirectBaseURI()+tppProcessId;
		String tpPNokRedirectURI = getTppNokRedirectBaseURI()+tppProcessId;
		Boolean tpPExplicitAuthorisationPreferred = false;
		PaymentCase case1 = loadPayment("paymentCase-1.yml");
		Map<String, String> response = null;
		try {
			response = (Map<String, String>) pis.initiatePayment(case1.getPayment(), 
					"payments", "sepa-credit-transfers", xRequestId, 
					"127.0.0.1", null, null, null, 
					case1.getPsuId(), null, null, null, null, 
					true, tpPRedirectURI, tpPNokRedirectURI, tpPExplicitAuthorisationPreferred, 
					null, null, null, null, null, 
					null, null, null, null);
		} catch (ApiException e) {
			throw new IllegalStateException(e);
		}
		ObjectMapper objectMapper = new ObjectMapper();
		objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
		PaymentInitationRequestResponse201 resp = objectMapper.convertValue(response.get("PaymentInitationRequestResponse201"), PaymentInitationRequestResponse201.class);

        Assert.assertNotNull(resp.getPaymentId());
        String redirectLink = (String) resp.getLinks().get("scaRedirect");
        Assert.assertNotNull(redirectLink);
        Assert.assertTrue(redirectLink.startsWith("http://localhost:8090/pis/auth?paymentId="+resp.getPaymentId()));
        
	}
	
	public PaymentCase loadPayment(String file) {
		InputStream stream = SinglePaymentRedirectIT.class.getResourceAsStream(file);
		try {
			return ymlMapper.readValue(stream, PaymentCase.class);
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}

}
