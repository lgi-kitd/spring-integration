/*
 * Copyright 2002-2010 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.integration.http.config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.Map;

import org.junit.Test;
import org.junit.runner.RunWith;

import org.springframework.beans.DirectFieldAccessor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationContext;
import org.springframework.expression.Expression;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.integration.MessageChannel;
import org.springframework.integration.endpoint.AbstractEndpoint;
import org.springframework.integration.http.outbound.HttpRequestExecutingMessageHandler;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.util.ObjectUtils;
import org.springframework.web.client.ResponseErrorHandler;

/**
 * @author Mark Fisher
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration
public class HttpOutboundGatewayParserTests {

	@Autowired @Qualifier("minimalConfig")
	private AbstractEndpoint minimalConfigEndpoint;

	@Autowired @Qualifier("fullConfig")
	private AbstractEndpoint fullConfigEndpoint;

	@Autowired
	private ApplicationContext applicationContext;


	@Test
	public void minimalConfig() {
		HttpRequestExecutingMessageHandler handler = (HttpRequestExecutingMessageHandler) new DirectFieldAccessor(
				this.minimalConfigEndpoint).getPropertyValue("handler");
		MessageChannel requestChannel = (MessageChannel) new DirectFieldAccessor(
				this.minimalConfigEndpoint).getPropertyValue("inputChannel");
		assertEquals(this.applicationContext.getBean("requests"), requestChannel);
		DirectFieldAccessor handlerAccessor = new DirectFieldAccessor(handler);
		Object replyChannel = handlerAccessor.getPropertyValue("outputChannel");
		assertNull(replyChannel);
		DirectFieldAccessor templateAccessor = new DirectFieldAccessor(handlerAccessor.getPropertyValue("restTemplate"));
		ClientHttpRequestFactory requestFactory = (ClientHttpRequestFactory)
				templateAccessor.getPropertyValue("requestFactory");
		assertTrue(requestFactory instanceof SimpleClientHttpRequestFactory);
		assertEquals("http://localhost/test1", handlerAccessor.getPropertyValue("uri"));
		assertEquals(HttpMethod.POST, handlerAccessor.getPropertyValue("httpMethod"));
		assertEquals("UTF-8", handlerAccessor.getPropertyValue("charset"));
		assertEquals(true, handlerAccessor.getPropertyValue("extractPayload"));
		assertEquals(false, handlerAccessor.getPropertyValue("transferCookies"));
	}

	@Test
	@SuppressWarnings("unchecked")
	public void fullConfig() throws Exception {
		DirectFieldAccessor endpointAccessor = new DirectFieldAccessor(this.fullConfigEndpoint);
		HttpRequestExecutingMessageHandler handler = (HttpRequestExecutingMessageHandler) endpointAccessor.getPropertyValue("handler");
		MessageChannel requestChannel = (MessageChannel) new DirectFieldAccessor(
				this.fullConfigEndpoint).getPropertyValue("inputChannel");
		assertEquals(this.applicationContext.getBean("requests"), requestChannel);
		DirectFieldAccessor handlerAccessor = new DirectFieldAccessor(handler);
		assertEquals(77, handlerAccessor.getPropertyValue("order"));
		assertEquals(Boolean.FALSE, endpointAccessor.getPropertyValue("autoStartup"));
		Object replyChannel = handlerAccessor.getPropertyValue("outputChannel");
		assertNotNull(replyChannel);
		assertEquals(this.applicationContext.getBean("replies"), replyChannel);
		DirectFieldAccessor templateAccessor = new DirectFieldAccessor(handlerAccessor.getPropertyValue("restTemplate"));
		ClientHttpRequestFactory requestFactory = (ClientHttpRequestFactory)
				templateAccessor.getPropertyValue("requestFactory");
		assertTrue(requestFactory instanceof SimpleClientHttpRequestFactory);
		Object converterListBean = this.applicationContext.getBean("converterList");
		assertEquals(converterListBean, templateAccessor.getPropertyValue("messageConverters"));
		assertEquals(String.class, handlerAccessor.getPropertyValue("expectedResponseType"));
		assertEquals("http://localhost/test2", handlerAccessor.getPropertyValue("uri"));
		assertEquals(HttpMethod.PUT, handlerAccessor.getPropertyValue("httpMethod"));
		assertEquals("UTF-8", handlerAccessor.getPropertyValue("charset"));
		assertEquals(false, handlerAccessor.getPropertyValue("extractPayload"));
		Object requestFactoryBean = this.applicationContext.getBean("testRequestFactory");
		assertEquals(requestFactoryBean, requestFactory);
		Object errorHandlerBean = this.applicationContext.getBean("testErrorHandler");
		assertEquals(errorHandlerBean, templateAccessor.getPropertyValue("errorHandler"));
		Object sendTimeout = new DirectFieldAccessor(
				handlerAccessor.getPropertyValue("messagingTemplate")).getPropertyValue("sendTimeout");
		assertEquals(new Long("1234"), sendTimeout);
		Map<String, Expression> uriVariableExpressions =
				(Map<String, Expression>) handlerAccessor.getPropertyValue("uriVariableExpressions");
		assertEquals(1, uriVariableExpressions.size());
		assertEquals("headers.bar", uriVariableExpressions.get("foo").getExpressionString());
		DirectFieldAccessor mapperAccessor = new DirectFieldAccessor(handlerAccessor.getPropertyValue("headerMapper"));
		String[] mappedRequestHeaders = (String[]) mapperAccessor.getPropertyValue("outboundHeaderNames");
		String[] mappedResponseHeaders = (String[]) mapperAccessor.getPropertyValue("inboundHeaderNames");
		assertEquals(2, mappedRequestHeaders.length);
		assertEquals(1, mappedResponseHeaders.length);
		assertTrue(ObjectUtils.containsElement(mappedRequestHeaders, "requestHeader1"));
		assertTrue(ObjectUtils.containsElement(mappedRequestHeaders, "requestHeader2"));
		assertEquals("responseHeader", mappedResponseHeaders[0]);
		assertEquals(true, handlerAccessor.getPropertyValue("transferCookies"));
	}


	public static class StubErrorHandler implements ResponseErrorHandler {

		public boolean hasError(ClientHttpResponse response) throws IOException {
			return false;
		}

		public void handleError(ClientHttpResponse response) throws IOException {
		}
	}

}
