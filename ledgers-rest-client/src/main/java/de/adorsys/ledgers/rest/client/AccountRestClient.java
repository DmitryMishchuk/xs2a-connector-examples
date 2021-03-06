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

package de.adorsys.ledgers.rest.client;

import org.springframework.cloud.netflix.feign.FeignClient;

import de.adorsys.ledgers.middleware.rest.resource.AccountRestAPI;

@FeignClient(value = "ledgersAccount", url = "${ledgers.url}", path=AccountRestAPI.BASE_PATH, configuration=FeignConfig.class)
public interface AccountRestClient extends AccountRestAPI {}
