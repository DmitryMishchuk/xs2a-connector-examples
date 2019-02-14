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

import de.adorsys.aspsp.xs2a.connector.spi.converter.ChallengeDataMapper;
import de.adorsys.aspsp.xs2a.connector.spi.converter.ScaLoginToPaymentResponseMapper;
import de.adorsys.aspsp.xs2a.connector.spi.converter.ScaMethodConverter;
import de.adorsys.ledgers.middleware.api.domain.payment.PaymentProductTO;
import de.adorsys.ledgers.middleware.api.domain.payment.PaymentTypeTO;
import de.adorsys.ledgers.middleware.api.domain.sca.*;
import de.adorsys.ledgers.middleware.api.service.TokenStorageService;
import de.adorsys.ledgers.rest.client.AuthRequestInterceptor;
import de.adorsys.ledgers.rest.client.PaymentRestClient;
import de.adorsys.ledgers.util.Ids;
import de.adorsys.psd2.xs2a.core.consent.AspspConsentData;
import de.adorsys.psd2.xs2a.core.sca.ChallengeData;
import de.adorsys.psd2.xs2a.spi.domain.SpiContextData;
import de.adorsys.psd2.xs2a.spi.domain.authorisation.SpiAuthenticationObject;
import de.adorsys.psd2.xs2a.spi.domain.authorisation.SpiAuthorisationStatus;
import de.adorsys.psd2.xs2a.spi.domain.authorisation.SpiAuthorizationCodeResult;
import de.adorsys.psd2.xs2a.spi.domain.authorisation.SpiScaConfirmation;
import de.adorsys.psd2.xs2a.spi.domain.common.SpiTransactionStatus;
import de.adorsys.psd2.xs2a.spi.domain.payment.response.SpiPaymentCancellationResponse;
import de.adorsys.psd2.xs2a.spi.domain.psu.SpiPsuData;
import de.adorsys.psd2.xs2a.spi.domain.response.SpiResponse;
import de.adorsys.psd2.xs2a.spi.domain.response.SpiResponseStatus;
import de.adorsys.psd2.xs2a.spi.service.PaymentCancellationSpi;
import de.adorsys.psd2.xs2a.spi.service.SpiPayment;
import feign.FeignException;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Component
public class PaymentCancellationSpiImpl implements PaymentCancellationSpi {
    private static final Logger logger = LoggerFactory.getLogger(PaymentCancellationSpiImpl.class);

    private final PaymentRestClient ledgersPayment;
    private final TokenStorageService tokenStorageService;
    private final ScaMethodConverter scaMethodConverter;
    private final AuthRequestInterceptor authRequestInterceptor;
    private final AspspConsentDataService consentDataService;
    private final GeneralAuthorisationService authorisationService;
    private final ChallengeDataMapper challengeDataMapper;
    private final ScaLoginToPaymentResponseMapper scaLoginToPaymentResponseMapper;

    public PaymentCancellationSpiImpl(PaymentRestClient ledgersRestClient,
                                      TokenStorageService tokenStorageService, ScaMethodConverter scaMethodConverter,
                                      AuthRequestInterceptor authRequestInterceptor, AspspConsentDataService consentDataService,
                                      GeneralAuthorisationService authorisationService, ChallengeDataMapper challengeDataMapper,
                                      ScaLoginToPaymentResponseMapper scaLoginToPaymentResponseMapper) {
        super();
        this.ledgersPayment = ledgersRestClient;
        this.tokenStorageService = tokenStorageService;
        this.scaMethodConverter = scaMethodConverter;
        this.authRequestInterceptor = authRequestInterceptor;
        this.consentDataService = consentDataService;
        this.authorisationService = authorisationService;
        this.challengeDataMapper = challengeDataMapper;
        this.scaLoginToPaymentResponseMapper = scaLoginToPaymentResponseMapper;
    }

    @Override
    public @NotNull SpiResponse<SpiPaymentCancellationResponse> initiatePaymentCancellation(@NotNull SpiContextData contextData, @NotNull SpiPayment payment, @NotNull AspspConsentData aspspConsentData) {
        SpiPaymentCancellationResponse response = new SpiPaymentCancellationResponse();
        response.setCancellationAuthorisationMandated(true);
        response.setTransactionStatus(SpiTransactionStatus.ACTC); //TODO to be fixed after implementation of https://git.adorsys.de/adorsys/xs2a/aspsp-xs2a/issues/633
        return SpiResponse.<SpiPaymentCancellationResponse>builder().aspspConsentData(aspspConsentData).payload(response).success();
    }

    /**
     * Makes no sense.
     */
    @Override
    public @NotNull SpiResponse<SpiResponse.VoidResponse> cancelPaymentWithoutSca(@NotNull SpiContextData contextData, @NotNull SpiPayment payment, @NotNull AspspConsentData aspspConsentData) {
        // TODO: current implementation of Ledgers doesn't support the payment cancellation without authorisation,
        // maybe this will be implemented in the future: https://git.adorsys.de/adorsys/xs2a/aspsp-xs2a/issues/669
        return SpiResponse.<SpiResponse.VoidResponse>builder().aspspConsentData(aspspConsentData).fail(SpiResponseStatus.NOT_SUPPORTED);
    }

    @Override
    public @NotNull SpiResponse<SpiResponse.VoidResponse> verifyScaAuthorisationAndCancelPayment(@NotNull SpiContextData contextData, @NotNull SpiScaConfirmation spiScaConfirmation, @NotNull SpiPayment payment, @NotNull AspspConsentData aspspConsentData) {
        try {
            SCAPaymentResponseTO sca = consentDataService.response(aspspConsentData, SCAPaymentResponseTO.class);
            authRequestInterceptor.setAccessToken(sca.getBearerToken().getAccess_token());

            ResponseEntity<SCAPaymentResponseTO> response = ledgersPayment.authorizeCancelPayment(sca.getPaymentId(), sca.getAuthorisationId(), spiScaConfirmation.getTanNumber());
            return response.getStatusCode() == HttpStatus.OK
                           ? SpiResponse.<SpiResponse.VoidResponse>builder().aspspConsentData(aspspConsentData).payload(SpiResponse.voidResponse()).success()
                           : SpiResponse.<SpiResponse.VoidResponse>builder().fail(SpiResponseStatus.LOGICAL_FAILURE);
        } catch (Exception e) {
            return SpiResponse.<SpiResponse.VoidResponse>builder().aspspConsentData(aspspConsentData)
                           .fail(SpiResponseStatus.LOGICAL_FAILURE);
        }
    }

    @Override
    public SpiResponse<SpiAuthorisationStatus> authorisePsu(@NotNull SpiContextData contextData, @NotNull SpiPsuData psuData, String password, SpiPayment businessObject, AspspConsentData aspspConsentData) {
        SCAPaymentResponseTO originalResponse = consentDataService.response(aspspConsentData, SCAPaymentResponseTO.class, false);
        String authorisationId = originalResponse != null && originalResponse.getAuthorisationId() != null
                                         ? originalResponse.getAuthorisationId()
                                         : Ids.id();
        SpiResponse<SpiAuthorisationStatus> authorisePsu = authorisationService.authorisePsuForConsent(
                psuData, password, businessObject.getPaymentId(), authorisationId, OpTypeTO.CANCEL_PAYMENT, aspspConsentData);

        if (!authorisePsu.isSuccessful()) {
            return authorisePsu;
        }
        SCAPaymentResponseTO scaPaymentResponse;
        AspspConsentData paymentAspspConsentData;
        try {
            scaPaymentResponse = toPaymentConsent(businessObject, authorisePsu, originalResponse);
            paymentAspspConsentData = authorisePsu.getAspspConsentData().respondWith(tokenStorageService.toBytes(scaPaymentResponse));
        } catch (IOException e) {
            return SpiResponse.<SpiAuthorisationStatus>builder()
                           .message(e.getMessage())
                           .aspspConsentData(aspspConsentData)
                           .fail(SpiResponseStatus.LOGICAL_FAILURE);
        }
        return SpiResponse.<SpiAuthorisationStatus>builder().payload(SpiAuthorisationStatus.SUCCESS).aspspConsentData(paymentAspspConsentData).success();
    }

    @Override
    public SpiResponse<List<SpiAuthenticationObject>> requestAvailableScaMethods(@NotNull SpiContextData contextData, SpiPayment businessObject, AspspConsentData aspspConsentData) {
        SCAPaymentResponseTO sca = consentDataService.response(aspspConsentData, SCAPaymentResponseTO.class);
        authRequestInterceptor.setAccessToken(sca.getBearerToken().getAccess_token());
        ResponseEntity<SCAPaymentResponseTO> cancelSCA = ledgersPayment.getCancelSCA(sca.getPaymentId(), sca.getAuthorisationId());

        List<SpiAuthenticationObject> authenticationObjectList = Optional.ofNullable(cancelSCA.getBody())
                                                                         .map(SCAResponseTO::getScaMethods)
                                                                         .map(scaMethodConverter::toSpiAuthenticationObjectList)
                                                                         .orElseGet(Collections::emptyList);
        return authenticationObjectList.isEmpty()
                       ? SpiResponse.<List<SpiAuthenticationObject>>builder().aspspConsentData(aspspConsentData).fail(SpiResponseStatus.LOGICAL_FAILURE)
                       : SpiResponse.<List<SpiAuthenticationObject>>builder().payload(authenticationObjectList).aspspConsentData(aspspConsentData).success();
    }

    @Override
    public @NotNull SpiResponse<SpiAuthorizationCodeResult> requestAuthorisationCode(@NotNull SpiContextData contextData, @NotNull String authenticationMethodId, @NotNull SpiPayment businessObject, @NotNull AspspConsentData aspspConsentData) {
        SCAPaymentResponseTO sca = consentDataService.response(aspspConsentData, SCAPaymentResponseTO.class);

        if (ScaStatusTO.PSUIDENTIFIED.equals(sca.getScaStatus()) || ScaStatusTO.PSUAUTHENTICATED.equals(sca.getScaStatus())) {
            try {
                authRequestInterceptor.setAccessToken(sca.getBearerToken().getAccess_token());
                logger.info("Request to generate SCA {}", sca.getPaymentId());
                ResponseEntity<SCAPaymentResponseTO> selectMethodResponse = ledgersPayment.selecCancelPaymentSCAtMethod(sca.getPaymentId(), sca.getAuthorisationId(), authenticationMethodId);
                logger.info("SCA was send, operationId is {}", sca.getPaymentId());
                sca = selectMethodResponse.getBody();
                return returnScaMethodSelection(aspspConsentData, sca);
            } catch (FeignException e) {
                return SpiResponse.<SpiAuthorizationCodeResult>builder()
                               .aspspConsentData(aspspConsentData)
                               .fail(SpiFailureResponseHelper.getSpiFailureResponse(e, logger));
            } finally {
                authRequestInterceptor.setAccessToken(null);
            }
        } else if (ScaStatusTO.SCAMETHODSELECTED.equals(sca.getScaStatus())) {
            return returnScaMethodSelection(aspspConsentData, sca);
        } else {
            return SpiResponse.<SpiAuthorizationCodeResult>builder()
                           .aspspConsentData(aspspConsentData)
                           .message(String.format("Wrong state. Expecting sca status to be %s if auth was sent or %s if auth code wasn't sent yet. But was %s.", ScaStatusTO.SCAMETHODSELECTED.name(), ScaStatusTO.PSUIDENTIFIED.name(), sca.getScaStatus().name()))
                           .fail(SpiResponseStatus.LOGICAL_FAILURE);
        }
    }

    private SCAPaymentResponseTO toPaymentConsent(SpiPayment spiPayment, SpiResponse<SpiAuthorisationStatus> authorisePsu, SCAPaymentResponseTO originalResponse) throws IOException {
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

    private SpiResponse<SpiAuthorizationCodeResult> returnScaMethodSelection(AspspConsentData aspspConsentData,
                                                                             SCAPaymentResponseTO sca) {
        SpiAuthorizationCodeResult spiAuthorizationCodeResult = new SpiAuthorizationCodeResult();
        ChallengeData challengeData = Optional.ofNullable(challengeDataMapper.toChallengeData(sca.getChallengeData())).orElse(new ChallengeData());
        spiAuthorizationCodeResult.setChallengeData(challengeData);
        spiAuthorizationCodeResult.setSelectedScaMethod(scaMethodConverter.toSpiAuthenticationObject(sca.getChosenScaMethod()));
        return SpiResponse.<SpiAuthorizationCodeResult>builder()
                       .aspspConsentData(consentDataService.store(sca, aspspConsentData))
                       .payload(spiAuthorizationCodeResult)
                       .success();
    }
}
