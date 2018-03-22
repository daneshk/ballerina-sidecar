/*
 *  Copyright (c) 2018, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 *  WSO2 Inc. licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package io.ballerina.springboot.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.ballerina.springboot.BallerinaTransactionRegistry;
import io.ballerina.springboot.bean.RegisterRequest;
import org.apache.http.impl.client.HttpClients;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.handler.HandlerInterceptorAdapter;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Ballerina Transaction Interceptor.
 * This interceptor register transactional request to Ballerina Initiator.
 *
 * @since 1.0.0
 */
@Component
public class TransactionInterceptor extends HandlerInterceptorAdapter {

    private final static Logger logger = LoggerFactory.getLogger(TransactionInterceptor.class);
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String txnID = request.getHeader("X-XID");
        String registerAtUrl = request.getHeader("X-Register-At-URL");

        if (txnID == null && registerAtUrl == null) {
            logger.warn("Request message does not contain transaction id or register url. service is not a " +
                    "transaction participant");
            return true;
        }
        RegisterRequest registerRequest = new RegisterRequest();
        registerRequest.setRegisterAtUrl(registerAtUrl);
        // TODO: figure out value needs to be send as transaction block id.
        registerRequest.setTransactionBlockId(1);
        registerRequest.setTransactionId(txnID);
        BallerinaTransactionRegistry.setTransactionId(txnID);

        if (logger.isDebugEnabled()) {
            logger.debug("Transaction Register request message: " + registerRequest);
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        // Jackson ObjectMapper to convert requestBody to JSON
        String json = new ObjectMapper().writeValueAsString(registerRequest);
        HttpEntity<String> entity = new HttpEntity<>(json, headers);

        ClientHttpRequestFactory requestFactory = new
                HttpComponentsClientHttpRequestFactory(HttpClients.createDefault());
        RestTemplate restTemplate = new RestTemplate(requestFactory);

//        TODO: uncomplement when connecting to ballerina side car
        ResponseEntity<String> responseEntity = restTemplate.exchange("http://10.100.1.182:33333/register",
                HttpMethod.POST, entity, String.class);

        if (responseEntity.getStatusCode().is2xxSuccessful()) {
            if (logger.isDebugEnabled()) {
                logger.debug("Txn Register Response code: " + responseEntity.getStatusCode().toString());
                logger.debug("Txn Register Response Msg: " + responseEntity.getBody());
            }
            return true;
        } else {
            logger.error("Terminating service call, Transaction registration failed with status code: " +
                    responseEntity.getStatusCode().toString());
            logger.error("Register Response Msg: " + responseEntity.getBody());
            return false;
        }
    }

    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, @Nullable ModelAndView modelAndView) throws Exception {
        super.postHandle(request, response, handler, modelAndView);
    }
}
