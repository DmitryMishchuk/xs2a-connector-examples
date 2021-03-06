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

import de.adorsys.aspsp.xs2a.connector.spi.converter.ScaLoginToPaymentResponseMapper;
import de.adorsys.aspsp.xs2a.connector.spi.converter.ScaMethodConverter;
import de.adorsys.ledgers.middleware.api.domain.payment.PaymentProductTO;
import de.adorsys.ledgers.middleware.api.domain.payment.PaymentTypeTO;
import de.adorsys.ledgers.middleware.api.domain.sca.OpTypeTO;
import de.adorsys.ledgers.middleware.api.domain.sca.SCALoginResponseTO;
import de.adorsys.ledgers.middleware.api.domain.sca.SCAPaymentResponseTO;
import de.adorsys.ledgers.middleware.api.domain.um.ScaUserDataTO;
import de.adorsys.ledgers.middleware.api.service.TokenStorageService;
import de.adorsys.ledgers.rest.client.AuthRequestInterceptor;
import de.adorsys.ledgers.rest.client.PaymentRestClient;
import de.adorsys.psd2.xs2a.core.consent.AspspConsentData;
import de.adorsys.psd2.xs2a.core.profile.PaymentType;
import de.adorsys.psd2.xs2a.spi.domain.SpiContextData;
import de.adorsys.psd2.xs2a.spi.domain.authorisation.SpiAuthenticationObject;
import de.adorsys.psd2.xs2a.spi.domain.authorisation.SpiAuthorisationStatus;
import de.adorsys.psd2.xs2a.spi.domain.authorisation.SpiAuthorizationCodeResult;
import de.adorsys.psd2.xs2a.spi.domain.payment.SpiBulkPayment;
import de.adorsys.psd2.xs2a.spi.domain.payment.SpiPeriodicPayment;
import de.adorsys.psd2.xs2a.spi.domain.payment.SpiSinglePayment;
import de.adorsys.psd2.xs2a.spi.domain.payment.response.SpiBulkPaymentInitiationResponse;
import de.adorsys.psd2.xs2a.spi.domain.payment.response.SpiPeriodicPaymentInitiationResponse;
import de.adorsys.psd2.xs2a.spi.domain.payment.response.SpiSinglePaymentInitiationResponse;
import de.adorsys.psd2.xs2a.spi.domain.psu.SpiPsuData;
import de.adorsys.psd2.xs2a.spi.domain.response.SpiResponse;
import de.adorsys.psd2.xs2a.spi.domain.response.SpiResponseStatus;
import de.adorsys.psd2.xs2a.spi.service.*;
import feign.FeignException;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

import static de.adorsys.ledgers.middleware.api.domain.sca.ScaStatusTO.*;

@Component
public class PaymentAuthorisationSpiImpl implements PaymentAuthorisationSpi {
    private static final Logger logger = LoggerFactory.getLogger(PaymentAuthorisationSpiImpl.class);
    private final GeneralAuthorisationService authorisationService;
    private final TokenStorageService tokenStorageService;
    private final ScaMethodConverter scaMethodConverter;
    private final ScaLoginToPaymentResponseMapper scaLoginToPaymentResponseMapper;
    private final AuthRequestInterceptor authRequestInterceptor;
    private final AspspConsentDataService consentDataService;
    private final PaymentRestClient paymentRestClient;
    private final SinglePaymentSpi singlePaymentSpi;
    private final BulkPaymentSpi bulkPaymentSpi;
    private final PeriodicPaymentSpi periodicPaymentSpi;
    private final CmsPaymentStatusUpdateService cmsPaymentStatusUpdateService;

    public PaymentAuthorisationSpiImpl(GeneralAuthorisationService authorisationService,
                                       TokenStorageService tokenStorageService, ScaMethodConverter scaMethodConverter,
                                       ScaLoginToPaymentResponseMapper scaLoginToPaymentResponseMapper,
                                       AuthRequestInterceptor authRequestInterceptor, AspspConsentDataService consentDataService,
                                       PaymentRestClient paymentRestClient, SinglePaymentSpi singlePaymentSpi, BulkPaymentSpi bulkPaymentSpi,
                                       PeriodicPaymentSpi periodicPaymentSpi, CmsPaymentStatusUpdateService cmsPaymentStatusUpdateService) {
        this.authorisationService = authorisationService;
        this.tokenStorageService = tokenStorageService;
        this.scaMethodConverter = scaMethodConverter;
        this.scaLoginToPaymentResponseMapper = scaLoginToPaymentResponseMapper;
        this.authRequestInterceptor = authRequestInterceptor;
        this.consentDataService = consentDataService;
        this.paymentRestClient = paymentRestClient;
        this.singlePaymentSpi = singlePaymentSpi;
        this.bulkPaymentSpi = bulkPaymentSpi;
        this.periodicPaymentSpi = periodicPaymentSpi;
        this.cmsPaymentStatusUpdateService = cmsPaymentStatusUpdateService;
    }

    @Override
    public SpiResponse<SpiAuthorisationStatus> authorisePsu(@NotNull SpiContextData contextData, @NotNull SpiPsuData psuLoginData, String pin, SpiPayment spiPayment, @NotNull AspspConsentData aspspConsentData) {
        SCAPaymentResponseTO originalResponse = consentDataService.response(aspspConsentData, SCAPaymentResponseTO.class, false);

        SpiResponse<SpiAuthorisationStatus> authorisePsu = authorisationService.authorisePsuForConsent(
                psuLoginData, pin, originalResponse.getPaymentId(), originalResponse, OpTypeTO.PAYMENT, aspspConsentData);

        if (!authorisePsu.isSuccessful()) {
            return authorisePsu;
        }

        try {
            SCAPaymentResponseTO scaPaymentResponse = toPaymentConsent(spiPayment, authorisePsu, originalResponse);
            cmsPaymentStatusUpdateService.updatePaymentStatus(spiPayment.getPaymentId(),authorisePsu.getAspspConsentData());
            AspspConsentData paymentAspspConsentData = authorisePsu.getAspspConsentData().respondWith(tokenStorageService.toBytes(scaPaymentResponse));
            return initiatePmtOnExemptedIfRequired(contextData, spiPayment, authorisePsu, scaPaymentResponse, paymentAspspConsentData);
        } catch (IOException e) {
            return SpiResponse.<SpiAuthorisationStatus>builder()
                           .message(e.getMessage())
                           .aspspConsentData(aspspConsentData)
                           .fail(SpiResponseStatus.LOGICAL_FAILURE);
        }
    }

    @Override
    public SpiResponse<List<SpiAuthenticationObject>> requestAvailableScaMethods(@NotNull SpiContextData contextData, SpiPayment spiPayment, @NotNull AspspConsentData aspspConsentData) {
        SCAPaymentResponseTO sca = consentDataService.response(aspspConsentData, SCAPaymentResponseTO.class);
        List<ScaUserDataTO> scaMethods = Optional.ofNullable(sca.getScaMethods()).orElse(Collections.emptyList());
        return SpiResponse.<List<SpiAuthenticationObject>>builder().payload(scaMethodConverter.toSpiAuthenticationObjectList(scaMethods))
                       .aspspConsentData(aspspConsentData).success();
    }

    @Override
    public @NotNull SpiResponse<SpiAuthorizationCodeResult> requestAuthorisationCode(@NotNull SpiContextData contextData, @NotNull String authenticationMethodId, @NotNull SpiPayment spiPayment, @NotNull AspspConsentData aspspConsentData) {
        SCAPaymentResponseTO sca = consentDataService.response(aspspConsentData, SCAPaymentResponseTO.class);
        if (EnumSet.of(PSUIDENTIFIED, PSUAUTHENTICATED).contains(sca.getScaStatus())) {
            try {
                authRequestInterceptor.setAccessToken(sca.getBearerToken().getAccess_token());
                logger.info("Request to generate SCA {}", sca.getPaymentId());
                ResponseEntity<SCAPaymentResponseTO> selectMethodResponse = paymentRestClient.selectMethod(sca.getPaymentId(), sca.getAuthorisationId(), authenticationMethodId);
                logger.info("SCA was send, operationId is {}", sca.getPaymentId());
                sca = selectMethodResponse.getBody();
                return authorisationService.returnScaMethodSelection(aspspConsentData, sca);
            } catch (FeignException e) {
                return SpiResponse.<SpiAuthorizationCodeResult>builder()
                               .aspspConsentData(aspspConsentData)
                               .fail(SpiFailureResponseHelper.getSpiFailureResponse(e, logger));
            } finally {
                authRequestInterceptor.setAccessToken(null);
            }
        } else {
            return authorisationService.getResponseIfScaSelected(aspspConsentData, sca);
        }
    }

    public SCAPaymentResponseTO toPaymentConsent(SpiPayment spiPayment, SpiResponse<SpiAuthorisationStatus> authorisePsu, SCAPaymentResponseTO originalResponse) throws IOException {
        String paymentTypeString = Optional.ofNullable(spiPayment.getPaymentType()).orElseThrow(() -> new IOException("Missing payment type")).name();
        SCALoginResponseTO scaResponseTO = tokenStorageService.fromBytes(authorisePsu.getAspspConsentData().getAspspConsentData(), SCALoginResponseTO.class);
        SCAPaymentResponseTO paymentResponse = scaLoginToPaymentResponseMapper.toPaymentResponse(scaResponseTO);
        paymentResponse.setObjectType(SCAPaymentResponseTO.class.getSimpleName());
        paymentResponse.setPaymentId(spiPayment.getPaymentId());
        paymentResponse.setPaymentType(PaymentTypeTO.valueOf(paymentTypeString));
        String paymentProduct2 = spiPayment.getPaymentProduct();
        if (paymentProduct2 == null && originalResponse != null && originalResponse.getPaymentProduct() != null) {
            paymentProduct2 = originalResponse.getPaymentProduct().getValue();
        } else {
            throw new IOException("Missing payment product");
        }
        final String pp = paymentProduct2;
        paymentResponse.setPaymentProduct(PaymentProductTO.getByValue(paymentProduct2).orElseThrow(() -> new IOException(String.format("Unsupported payment product %s", pp))));
        return paymentResponse;
    }

    private SpiResponse<SpiAuthorisationStatus> initiatePmtOnExemptedIfRequired(@NotNull SpiContextData contextData, SpiPayment spiPayment, SpiResponse<SpiAuthorisationStatus> authorisePsu, SCAPaymentResponseTO scaPaymentResponse, AspspConsentData paymentAspspConsentData) {
        if (EnumSet.of(EXEMPTED, PSUAUTHENTICATED, PSUIDENTIFIED)
                    .contains(scaPaymentResponse.getScaStatus())) {
            return initiatePaymentOnExemptedSCA(contextData, spiPayment, authorisePsu, paymentAspspConsentData);
        }
        return new SpiResponse<>(authorisePsu.getPayload(), paymentAspspConsentData);
    }

    private SpiResponse<SpiAuthorisationStatus> initiatePaymentOnExemptedSCA(SpiContextData contextData, SpiPayment spiPayment,
                                                                             SpiResponse<SpiAuthorisationStatus> authorisePsu, AspspConsentData paymentAspspConsentData) {

        // Payment initiation can only be called if exemption.
        PaymentType paymentType = spiPayment.getPaymentType();
        switch (paymentType) {
            case SINGLE:
                SpiResponse<SpiSinglePaymentInitiationResponse> initiatePayment =
                        singlePaymentSpi.initiatePayment(contextData, (@NotNull SpiSinglePayment) spiPayment, paymentAspspConsentData);
                return new SpiResponse<>(authorisePsu.getPayload(), initiatePayment.getAspspConsentData());
            case BULK:
                SpiResponse<SpiBulkPaymentInitiationResponse> initiatePayment2 =
                        bulkPaymentSpi.initiatePayment(contextData, (@NotNull SpiBulkPayment) spiPayment, paymentAspspConsentData);
                return new SpiResponse<>(authorisePsu.getPayload(), initiatePayment2.getAspspConsentData());
            case PERIODIC:
                SpiResponse<SpiPeriodicPaymentInitiationResponse> initiatePayment3 =
                        periodicPaymentSpi.initiatePayment(contextData, (@NotNull SpiPeriodicPayment) spiPayment, paymentAspspConsentData);
                return new SpiResponse<>(authorisePsu.getPayload(), initiatePayment3.getAspspConsentData());
            default:
                // throw unsupported payment type
                return SpiResponse.<SpiAuthorisationStatus>builder()
                               .message(String.format("Unknown payment type %s", paymentType.getValue()))
                               .fail(SpiResponseStatus.LOGICAL_FAILURE);
        }
    }
}
