/*
 * Copyright (c) 2022, WSO2 LLC. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *
 */

package org.wso2.carbon.apimgt.impl;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.apimgt.api.APIConsumer;
import org.wso2.carbon.apimgt.api.APIEndpointUrlExtractor;
import org.wso2.carbon.apimgt.api.APIManagementException;
import org.wso2.carbon.apimgt.api.APIMgtResourceNotFoundException;
import org.wso2.carbon.apimgt.api.model.APIRevisionDeployment;
import org.wso2.carbon.apimgt.api.model.ApiTypeWrapper;
import org.wso2.carbon.apimgt.api.model.Environment;
import org.wso2.carbon.apimgt.api.model.VHost;
import org.wso2.carbon.apimgt.api.model.endpointurlextractor.EndpointUrl;
import org.wso2.carbon.apimgt.impl.utils.APIUtil;
import org.wso2.carbon.apimgt.impl.utils.VHostUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.wso2.carbon.apimgt.impl.utils.APIEndpointUrlExtractorUtils.getLoggedInUserConsumer;

/**
 * This class implements the API endpoint URLs extractor functionality.
 */
public class APIEndpointUrlExtractorImpl implements APIEndpointUrlExtractor {

    private static final Log log = LogFactory.getLog(APIEndpointUrlExtractorImpl.class);

    public APIEndpointUrlExtractorImpl() {
    }

    @Override
    public List<EndpointUrl> getApiEndpointUrls(ApiTypeWrapper apiTypeWrapper, Map<String, String> hostsWithSchemes)
            throws APIManagementException {

        List<EndpointUrl> endpointUrls = new ArrayList<>();

        String context = apiTypeWrapper.getContext();
        Boolean isDefaultVersion = false;

        if (apiTypeWrapper.getIsDefaultVersion() != null && apiTypeWrapper.getIsDefaultVersion()) {
            context = context.replaceAll("/" + apiTypeWrapper.getVersion() + "$", "");
            isDefaultVersion = true;
        }

        boolean isWs = StringUtils.equalsIgnoreCase("WS", apiTypeWrapper.getType());
        if (!isWs) {
            if (apiTypeWrapper.getTransports().contains(APIConstants.HTTP_PROTOCOL) &&
                    hostsWithSchemes.containsKey(APIConstants.HTTP_PROTOCOL)) {

                String host = hostsWithSchemes.get(APIConstants.HTTP_PROTOCOL).trim()
                        .replace(APIConstants.HTTP_PROTOCOL_URL_PREFIX, "");
                String url = APIConstants.HTTP_PROTOCOL + "://" + host + context;
                endpointUrls.add(new EndpointUrl(url, host, context, APIConstants.HTTP_PROTOCOL, isDefaultVersion));
            }
            if (apiTypeWrapper.getTransports().contains(APIConstants.HTTPS_PROTOCOL) &&
                    hostsWithSchemes.containsKey(APIConstants.HTTPS_PROTOCOL)) {

                String host = hostsWithSchemes.get(APIConstants.HTTPS_PROTOCOL).trim()
                        .replace(APIConstants.HTTPS_PROTOCOL_URL_PREFIX, "");
                String url = APIConstants.HTTPS_PROTOCOL + "://" + host + context;
                endpointUrls.add(new EndpointUrl(url, host, context, APIConstants.HTTPS_PROTOCOL, isDefaultVersion));
            }
        } else {
            String wsHost = hostsWithSchemes.get(APIConstants.WS_PROTOCOL).trim()
                    .replace(APIConstants.WS_PROTOCOL, "");
            String wsUrl = APIConstants.WS_PROTOCOL + "://" + wsHost + context;
            endpointUrls.add(new EndpointUrl(wsUrl, wsHost, context, APIConstants.WS_PROTOCOL, isDefaultVersion));

            String wssHost = hostsWithSchemes.get(APIConstants.WSS_PROTOCOL).trim()
                    .replace(APIConstants.WSS_PROTOCOL, "");
            String wssUrl = APIConstants.WSS_PROTOCOL + "://" + wssHost + context;
            endpointUrls.add(new EndpointUrl(wssUrl, wssHost, context, APIConstants.WSS_PROTOCOL, isDefaultVersion));
        }
        return endpointUrls;
    }

    @Override
    public Map<String, String> getHostWithSchemeMappingForEnvironment(
            ApiTypeWrapper apiTypeWrapper, String tenantDomainOrOrganization, String environmentName)
            throws APIManagementException {

        Map<String, String> hostsWithSchemes = new HashMap<>();
        APIConsumer apiConsumer = getLoggedInUserConsumer();

        Map<String, String> domains = new HashMap<>();
        if (tenantDomainOrOrganization != null) {
            domains = APIUtil.getDomainMappings(tenantDomainOrOrganization,
                    APIConstants.API_DOMAIN_MAPPINGS_GATEWAY);
        }

        VHost vhost;

        if (!domains.isEmpty()) {
            // Custom gateway URL of tenant/organization
            String customUrl = domains.get(APIConstants.CUSTOM_URL);

            if (!StringUtils.contains(customUrl, "://")) {
                customUrl = APIConstants.HTTPS_PROTOCOL_URL_PREFIX + customUrl;
            }

            vhost = VHost.fromEndpointUrls(new String[]{customUrl});
        } else {
            Map<String, Environment> allEnvironments = APIUtil.getEnvironments(tenantDomainOrOrganization);
            Environment environment = allEnvironments.get(environmentName);

            if (environment == null) {
                throw new APIMgtResourceNotFoundException("Could not find provided environment '"
                        + environmentName + "'");
            }

            String host = "";

            List<APIRevisionDeployment> revisionDeployments =
                    apiConsumer.getAPIRevisionDeploymentListOfAPI(apiTypeWrapper.getUuid());
            for (APIRevisionDeployment revisionDeployment : revisionDeployments) {
                if (!revisionDeployment.isDisplayOnDevportal()) {
                    continue;
                }
                if (StringUtils.equals(revisionDeployment.getDeployment(), environmentName)) {
                    host = revisionDeployment.getVhost();
                }
            }

            if (StringUtils.isEmpty(host)) {
                // returns empty server urls
                hostsWithSchemes.put(APIConstants.HTTP_PROTOCOL, "");
                return hostsWithSchemes;
            }

            vhost = VHostUtils.getVhostFromEnvironment(environment, host);
        }

        boolean isWs = StringUtils.equalsIgnoreCase("WS", apiTypeWrapper.getType());
        if (!isWs) {
            if (apiTypeWrapper.getTransports().contains(APIConstants.HTTP_PROTOCOL)) {
                hostsWithSchemes.put(APIConstants.HTTP_PROTOCOL, vhost.getHttpUrl());
            }
            if (apiTypeWrapper.getTransports().contains(APIConstants.HTTPS_PROTOCOL)) {
                hostsWithSchemes.put(APIConstants.HTTPS_PROTOCOL, vhost.getHttpsUrl());
            }
        } else {
            hostsWithSchemes.put(APIConstants.WS_PROTOCOL, vhost.getWsUrl());
            hostsWithSchemes.put(APIConstants.WSS_PROTOCOL, vhost.getWssUrl());
        }
        return hostsWithSchemes;
    }
}
