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

import de.adorsys.aspsp.xs2a.connector.spi.converter.LedgersSpiAccountMapper;
import de.adorsys.ledgers.middleware.api.domain.account.FundsConfirmationRequestTO;
import de.adorsys.ledgers.middleware.api.domain.sca.SCAResponseTO;
import de.adorsys.ledgers.rest.client.AccountRestClient;
import de.adorsys.ledgers.rest.client.AuthRequestInterceptor;
import de.adorsys.psd2.xs2a.core.consent.AspspConsentData;
import de.adorsys.psd2.xs2a.core.piis.PiisConsent;
import de.adorsys.psd2.xs2a.spi.domain.SpiContextData;
import de.adorsys.psd2.xs2a.spi.domain.fund.SpiFundsConfirmationRequest;
import de.adorsys.psd2.xs2a.spi.domain.fund.SpiFundsConfirmationResponse;
import de.adorsys.psd2.xs2a.spi.domain.response.SpiResponse;
import de.adorsys.psd2.xs2a.spi.domain.response.SpiResponseStatus;
import de.adorsys.psd2.xs2a.spi.service.FundsConfirmationSpi;
import feign.FeignException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class FundsConfirmationSpiImpl implements FundsConfirmationSpi {
    private static final Logger logger = LoggerFactory.getLogger(SinglePaymentSpiImpl.class);

    private final AccountRestClient restClient;
    private final LedgersSpiAccountMapper accountMapper;
	private final AuthRequestInterceptor authRequestInterceptor;
	private final AspspConsentDataService tokenService;

    public FundsConfirmationSpiImpl(AccountRestClient restClient, LedgersSpiAccountMapper accountMapper,
			AuthRequestInterceptor authRequestInterceptor, AspspConsentDataService tokenService) {
		this.restClient = restClient;
		this.accountMapper = accountMapper;
		this.authRequestInterceptor = authRequestInterceptor;
		this.tokenService = tokenService;
	}
    
	/*
     * We accept any response with valid bearer token for the fund confirmation.
     *  
     */
	@Override
	public @NotNull SpiResponse<SpiFundsConfirmationResponse> performFundsSufficientCheck(@NotNull SpiContextData contextData,
			@Nullable PiisConsent piisConsent, @NotNull SpiFundsConfirmationRequest spiFundsConfirmationRequest,
			@NotNull AspspConsentData aspspConsentData) {
        try {
			SCAResponseTO sca = tokenService.response(aspspConsentData);
			authRequestInterceptor.setAccessToken(sca.getBearerToken().getAccess_token());

            logger.info("Funds confirmation request e={}", spiFundsConfirmationRequest);
            FundsConfirmationRequestTO request = accountMapper.toFundsConfirmationTO(contextData.getPsuData(), spiFundsConfirmationRequest);
            Boolean fundsAvailable = restClient.fundsConfirmation(request).getBody();
            logger.info("And got the response ={}", fundsAvailable);

            SpiFundsConfirmationResponse spiFundsConfirmationResponse = new SpiFundsConfirmationResponse();
            spiFundsConfirmationResponse.setFundsAvailable(Optional.ofNullable(fundsAvailable).orElse(false));
            return SpiResponse.<SpiFundsConfirmationResponse>builder()
                           .aspspConsentData(aspspConsentData)
                           .payload(spiFundsConfirmationResponse)
                           .success();
        } catch (FeignException e) {
            return SpiResponse.<SpiFundsConfirmationResponse>builder()
                           .aspspConsentData(aspspConsentData)
                           .fail(getSpiFailureResponse(e));
		} finally {
			authRequestInterceptor.setAccessToken(null);
		}
	}
	
	@NotNull
    private SpiResponseStatus getSpiFailureResponse(FeignException e) {
        logger.error(e.getMessage(), e);
        return e.status() == 500
                       ? SpiResponseStatus.TECHNICAL_FAILURE
                       : SpiResponseStatus.LOGICAL_FAILURE;
    }

}
