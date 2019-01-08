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

package de.adorsys.aspsp.xs2a.spi.impl;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import de.adorsys.aspsp.xs2a.spi.converter.LedgersSpiPaymentMapper;
import de.adorsys.ledgers.middleware.api.domain.payment.BulkPaymentTO;
import de.adorsys.ledgers.middleware.api.domain.payment.PaymentProductTO;
import de.adorsys.ledgers.middleware.api.domain.payment.PaymentTypeTO;
import de.adorsys.ledgers.middleware.api.domain.sca.SCAPaymentResponseTO;
import de.adorsys.ledgers.middleware.api.domain.sca.SCAResponseTO;
import de.adorsys.ledgers.middleware.api.domain.sca.ScaStatusTO;
import de.adorsys.ledgers.rest.client.AuthRequestInterceptor;
import de.adorsys.ledgers.rest.client.PaymentRestClient;
import de.adorsys.psd2.xs2a.core.consent.AspspConsentData;
import de.adorsys.psd2.xs2a.spi.domain.SpiContextData;
import de.adorsys.psd2.xs2a.spi.domain.authorisation.SpiScaConfirmation;
import de.adorsys.psd2.xs2a.spi.domain.common.SpiTransactionStatus;
import de.adorsys.psd2.xs2a.spi.domain.payment.SpiBulkPayment;
import de.adorsys.psd2.xs2a.spi.domain.payment.response.SpiBulkPaymentInitiationResponse;
import de.adorsys.psd2.xs2a.spi.domain.response.SpiResponse;
import de.adorsys.psd2.xs2a.spi.domain.response.SpiResponse.VoidResponse;
import de.adorsys.psd2.xs2a.spi.domain.response.SpiResponseStatus;
import de.adorsys.psd2.xs2a.spi.service.BulkPaymentSpi;
import feign.FeignException;
import feign.Response;


@Component
public class BulkPaymentSpiImpl implements BulkPaymentSpi {
    private static final Logger logger = LoggerFactory.getLogger(BulkPaymentSpiImpl.class);

    private final PaymentRestClient ledgersPayment;
    private final LedgersSpiPaymentMapper paymentMapper;
    private final GeneralPaymentService paymentService;
	private final AuthRequestInterceptor authRequestInterceptor;
	private final AspspConsentDataService tokenService;

	public BulkPaymentSpiImpl(PaymentRestClient ledgersRestClient, LedgersSpiPaymentMapper paymentMapper,
			GeneralPaymentService paymentService, AuthRequestInterceptor authRequestInterceptor,
			AspspConsentDataService tokenService) {
		super();
		this.ledgersPayment = ledgersRestClient;
		this.paymentMapper = paymentMapper;
		this.paymentService = paymentService;
		this.authRequestInterceptor = authRequestInterceptor;
		this.tokenService = tokenService;
	}

	@NotNull
    @Override
    public SpiResponse<SpiBulkPaymentInitiationResponse> initiatePayment(@NotNull SpiContextData contextData, @NotNull SpiBulkPayment payment, @NotNull AspspConsentData initialAspspConsentData) {
		try {
			SCAPaymentResponseTO response = initiatePaymentInternal(payment, initialAspspConsentData);
	        SpiBulkPaymentInitiationResponse spiInitiationResponse = Optional.ofNullable(response)
	        		.map(paymentMapper::toSpiBulkResponse)
	        		.orElseThrow(() -> FeignException.errorStatus("Request failed, Response was 201, but body was empty!", Response.builder().status(400).build()));
	        return SpiResponse.<SpiBulkPaymentInitiationResponse>builder()
	        		.aspspConsentData(tokenService.store(response, initialAspspConsentData))
	        		.message(response.getScaStatus().name())
	        		.payload(spiInitiationResponse)
	        		.success();
        } catch (FeignException e) {
            return SpiResponse.<SpiBulkPaymentInitiationResponse>builder()
                           .aspspConsentData(initialAspspConsentData.respondWith(initialAspspConsentData.getAspspConsentData()))
                           .fail(getSpiFailureResponse(e));
		}
    }

    @Override
    public @NotNull SpiResponse<SpiBulkPayment> getPaymentById(@NotNull SpiContextData contextData, @NotNull SpiBulkPayment payment, @NotNull AspspConsentData aspspConsentData) {
        try {
			SCAPaymentResponseTO sca = tokenService.response(aspspConsentData, SCAPaymentResponseTO.class);
			authRequestInterceptor.setAccessToken(sca.getBearerToken().getAccess_token());

            logger.info("Get payment by id with type={}, and id={}", PaymentTypeTO.BULK, payment.getPaymentId());
            logger.debug("Bulk payment body={}", payment);
            // Normally the paymentid contained here must match the payment id 
            // String paymentId = sca.getPaymentId(); This could also be used.
            // TODO: store payment type in sca.
            BulkPaymentTO response = (BulkPaymentTO) ledgersPayment.getPaymentById(payment.getPaymentId()).getBody();
            SpiBulkPayment spiBulkPayment = Optional.ofNullable(response)
                                                    .map(paymentMapper::mapToSpiBulkPayment)
                                                    .orElseThrow(() -> FeignException.errorStatus("Request failed, Response was 200, but body was empty!", Response.builder().status(400).build()));
            return SpiResponse.<SpiBulkPayment>builder()
                           .aspspConsentData(aspspConsentData.respondWith(aspspConsentData.getAspspConsentData()))
                           .payload(spiBulkPayment)
                           .success();
        } catch (FeignException e) {
            return SpiResponse.<SpiBulkPayment>builder()
                           .aspspConsentData(aspspConsentData.respondWith(aspspConsentData.getAspspConsentData()))
                           .fail(getSpiFailureResponse(e));
		} finally {
			authRequestInterceptor.setAccessToken(null);
		}
    }

    @Override
    public @NotNull SpiResponse<SpiTransactionStatus> getPaymentStatusById(@NotNull SpiContextData contextData, @NotNull SpiBulkPayment payment, @NotNull AspspConsentData aspspConsentData) {
        return paymentService.getPaymentStatusById(PaymentTypeTO.valueOf(payment.getPaymentType().name()), payment.getPaymentId(), aspspConsentData);
    }

	@Override
	public @NotNull SpiResponse<VoidResponse> executePaymentWithoutSca(@NotNull SpiContextData contextData,
			@NotNull SpiBulkPayment payment, @NotNull AspspConsentData aspspConsentData) {
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
	public @NotNull SpiResponse<VoidResponse> verifyScaAuthorisationAndExecutePayment(
			@NotNull SpiContextData contextData, @NotNull SpiScaConfirmation spiScaConfirmation,
			@NotNull SpiBulkPayment payment, @NotNull AspspConsentData aspspConsentData) {
        return paymentService.verifyScaAuthorisationAndExecutePayment(
                payment.getPaymentId(),
                PaymentProductTO.valueOf(payment.getPaymentProduct()),
                PaymentTypeTO.BULK,
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
    

	private SCAPaymentResponseTO initiatePaymentInternal(SpiBulkPayment payment,
			AspspConsentData initialAspspConsentData) {
		try {
			SCAResponseTO sca = tokenService.response(initialAspspConsentData);
			authRequestInterceptor.setAccessToken(sca.getBearerToken().getAccess_token());

		    logger.info("Initiate bulk payment with type={}", PaymentTypeTO.BULK);
		    logger.debug("Bulk payment body={}", payment);
		    BulkPaymentTO request = paymentMapper.toBulkPaymentTO(payment);
		    return ledgersPayment.initiatePayment(PaymentTypeTO.BULK, request).getBody();
		} finally {
			authRequestInterceptor.setAccessToken(null);
		}
	}
    
}