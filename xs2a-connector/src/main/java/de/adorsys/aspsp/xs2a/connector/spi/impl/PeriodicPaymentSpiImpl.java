/*
 * Copyright 2018-2018 adorsys GmbH & Co KG
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.adorsys.aspsp.xs2a.connector.spi.impl;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.jetbrains.annotations.NotNull;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import de.adorsys.aspsp.xs2a.connector.spi.converter.LedgersSpiPaymentMapper;
import de.adorsys.ledgers.middleware.api.domain.payment.PaymentProductTO;
import de.adorsys.ledgers.middleware.api.domain.payment.PaymentTypeTO;
import de.adorsys.ledgers.middleware.api.domain.payment.PeriodicPaymentTO;
import de.adorsys.ledgers.middleware.api.domain.sca.SCAPaymentResponseTO;
import de.adorsys.ledgers.middleware.api.domain.sca.SCAResponseTO;
import de.adorsys.ledgers.middleware.api.domain.sca.ScaStatusTO;
import de.adorsys.ledgers.rest.client.AuthRequestInterceptor;
import de.adorsys.ledgers.rest.client.PaymentRestClient;
import de.adorsys.psd2.xs2a.core.consent.AspspConsentData;
import de.adorsys.psd2.xs2a.spi.domain.SpiContextData;
import de.adorsys.psd2.xs2a.spi.domain.authorisation.SpiScaConfirmation;
import de.adorsys.psd2.xs2a.spi.domain.common.SpiTransactionStatus;
import de.adorsys.psd2.xs2a.spi.domain.payment.SpiPeriodicPayment;
import de.adorsys.psd2.xs2a.spi.domain.payment.response.SpiPeriodicPaymentInitiationResponse;
import de.adorsys.psd2.xs2a.spi.domain.response.SpiResponse;
import de.adorsys.psd2.xs2a.spi.domain.response.SpiResponseStatus;
import de.adorsys.psd2.xs2a.spi.service.PeriodicPaymentSpi;
import feign.FeignException;
import feign.Response;

@Component
public class PeriodicPaymentSpiImpl implements PeriodicPaymentSpi {
    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(PeriodicPaymentSpiImpl.class);

    private final PaymentRestClient ledgersRestClient;
    private final LedgersSpiPaymentMapper paymentMapper;
    private final GeneralPaymentService paymentService;
	private final AuthRequestInterceptor authRequestInterceptor;
	private final AspspConsentDataService tokenService;

	public PeriodicPaymentSpiImpl(PaymentRestClient ledgersRestClient, LedgersSpiPaymentMapper paymentMapper,
			GeneralPaymentService paymentService, AuthRequestInterceptor authRequestInterceptor,
			AspspConsentDataService tokenService) {
		super();
		this.ledgersRestClient = ledgersRestClient;
		this.paymentMapper = paymentMapper;
		this.paymentService = paymentService;
		this.authRequestInterceptor = authRequestInterceptor;
		this.tokenService = tokenService;
	}

	@Override
    public @NotNull SpiResponse<SpiPeriodicPaymentInitiationResponse> initiatePayment(@NotNull SpiContextData contextData, @NotNull SpiPeriodicPayment payment, @NotNull AspspConsentData initialAspspConsentData) {
		try {
			SCAPaymentResponseTO response = initiatePaymentInternal(payment, initialAspspConsentData);
	        SpiPeriodicPaymentInitiationResponse spiInitiationResponse = Optional.ofNullable(response)
	        		.map(paymentMapper::toSpiPeriodicResponse)
	        		.orElseThrow(() -> FeignException.errorStatus("Request failed, Response was 201, but body was empty!", Response.builder().status(400).build()));
	        return SpiResponse.<SpiPeriodicPaymentInitiationResponse>builder()
	        		.aspspConsentData(tokenService.store(response, initialAspspConsentData))
	        		.message(response.getScaStatus().name())
	        		.payload(spiInitiationResponse)
	        		.success();
        } catch (FeignException e) {
            return SpiResponse.<SpiPeriodicPaymentInitiationResponse>builder()
                           .aspspConsentData(initialAspspConsentData.respondWith(initialAspspConsentData.getAspspConsentData()))
                           .fail(getSpiFailureResponse(e));
		}
    }

    @Override
    public @NotNull SpiResponse<SpiPeriodicPayment> getPaymentById(@NotNull SpiContextData contextData, @NotNull SpiPeriodicPayment payment, @NotNull AspspConsentData aspspConsentData) {
        try {
			SCAPaymentResponseTO sca = tokenService.response(aspspConsentData, SCAPaymentResponseTO.class);
			authRequestInterceptor.setAccessToken(sca.getBearerToken().getAccess_token());

			logger.info("Get payment by id with type={}, and id={}", PaymentTypeTO.PERIODIC, payment.getPaymentId());
            logger.debug("Periodic payment body={}", payment);

            // String paymentId = sca.getPaymentId(); This could also be used.
            // TODO: store payment type in sca.
            PeriodicPaymentTO response = (PeriodicPaymentTO) ledgersRestClient.getPaymentById(payment.getPaymentId()).getBody();
            SpiPeriodicPayment spiPeriodicPayment = Optional.ofNullable(response)
                                                            .map(paymentMapper::mapToSpiPeriodicPayment)
                                                            .orElseThrow(() -> FeignException.errorStatus("Request failed, Response was 200, but body was empty!", Response.builder().status(400).build()));
            return SpiResponse.<SpiPeriodicPayment>builder()
                           .aspspConsentData(aspspConsentData.respondWith(aspspConsentData.getAspspConsentData()))
                           .payload(spiPeriodicPayment)
                           .success();

        } catch (FeignException e) {
            return SpiResponse.<SpiPeriodicPayment>builder()
                           .aspspConsentData(aspspConsentData.respondWith(aspspConsentData.getAspspConsentData()))
                           .fail(getSpiFailureResponse(e));
		} finally {
			authRequestInterceptor.setAccessToken(null);
		}
    }

    @Override
    public @NotNull SpiResponse<SpiTransactionStatus> getPaymentStatusById(@NotNull SpiContextData contextData, @NotNull SpiPeriodicPayment payment, @NotNull AspspConsentData aspspConsentData) {
        return paymentService.getPaymentStatusById(PaymentTypeTO.valueOf(payment.getPaymentType().name()), payment.getPaymentId(), aspspConsentData);
    }

    @Override
    public @NotNull SpiResponse<SpiResponse.VoidResponse> executePaymentWithoutSca(@NotNull SpiContextData contextData, @NotNull SpiPeriodicPayment payment, @NotNull AspspConsentData aspspConsentData) {
		try {
			SCAPaymentResponseTO response = initiatePaymentInternal(payment, aspspConsentData);
			if(ScaStatusTO.EXEMPTED.equals(response.getScaStatus())){
				// Success
				List<String> messages = Arrays.asList(response.getScaStatus().name(), String.format("Payment scheduled for execution. Transaction status is %s. Als see sca status", response.getTransactionStatus()));
				return SpiResponse.<SpiResponse.VoidResponse>builder()
						.aspspConsentData(tokenService.store(response, aspspConsentData))
						.message(messages)
						.success();
			}
			List<String> messages = Arrays.asList(response.getScaStatus().name(), String.format("Payment not executed. Transaction status is %s. Als see sca status", response.getTransactionStatus()));
			return SpiResponse.<SpiResponse.VoidResponse>builder()
					.aspspConsentData(tokenService.store(response, aspspConsentData))
					.message(messages)
					.fail(SpiResponseStatus.LOGICAL_FAILURE);
        } catch (FeignException e) {
            return SpiResponse.<SpiResponse.VoidResponse>builder()
                           .aspspConsentData(aspspConsentData.respondWith(aspspConsentData.getAspspConsentData()))
                           .fail(getSpiFailureResponse(e));
		}
    }

    @Override
    public @NotNull SpiResponse<SpiResponse.VoidResponse> verifyScaAuthorisationAndExecutePayment(@NotNull SpiContextData contextData, @NotNull SpiScaConfirmation spiScaConfirmation, @NotNull SpiPeriodicPayment payment, @NotNull AspspConsentData aspspConsentData) {
        return paymentService.verifyScaAuthorisationAndExecutePayment(
                payment.getPaymentId(),
                PaymentProductTO.valueOf(payment.getPaymentProduct()),
                PaymentTypeTO.PERIODIC,
                payment.toString(),
                spiScaConfirmation,
                aspspConsentData
        );
    }

    @NotNull
    private SpiResponseStatus getSpiFailureResponse(FeignException e) {
        logger.error(e.getMessage(), e);
        return e.status() == 500
                       ? SpiResponseStatus.TECHNICAL_FAILURE
                       : SpiResponseStatus.LOGICAL_FAILURE;
    }
    
	private SCAPaymentResponseTO initiatePaymentInternal(SpiPeriodicPayment payment,
			AspspConsentData initialAspspConsentData) {
		try {
			SCAResponseTO sca = tokenService.response(initialAspspConsentData);
			authRequestInterceptor.setAccessToken(sca.getBearerToken().getAccess_token());

			logger.info("Initiate periodic payment with type={}", PaymentTypeTO.PERIODIC);
		    logger.debug("Periodic payment body={}", payment);
		    PeriodicPaymentTO request = paymentMapper.toPeriodicPaymentTO(payment);
		    return ledgersRestClient.initiatePayment(PaymentTypeTO.PERIODIC, request).getBody();
		} finally {
			authRequestInterceptor.setAccessToken(null);
		}
	}

    
}
