/*
 * Copyright 2002-2008 the original author or authors.
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

package org.springframework.integration.gateway;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.Test;

import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.channel.MessageChannel;
import org.springframework.integration.channel.PollableChannel;
import org.springframework.integration.channel.QueueChannel;
import org.springframework.integration.message.Message;
import org.springframework.integration.message.StringMessage;

/**
 * @author Mark Fisher
 */
public class GatewayProxyFactoryBeanTests {

	@Test
	public void testRequestReplyWithAnonymousChannel() throws Exception {
		QueueChannel requestChannel = new QueueChannel();
		startResponder(requestChannel);
		GatewayProxyFactoryBean proxyFactory = new GatewayProxyFactoryBean();
		proxyFactory.setDefaultRequestChannel(requestChannel);
		proxyFactory.setServiceInterface(TestService.class);
		proxyFactory.afterPropertiesSet();
		TestService service = (TestService) proxyFactory.getObject();
		String result = service.requestReply("foo");
		assertEquals("foobar", result);
	}

	@Test
	public void testOneWay() throws Exception {
		final QueueChannel requestChannel = new QueueChannel();
		GatewayProxyFactoryBean proxyFactory = new GatewayProxyFactoryBean();
		proxyFactory.setServiceInterface(TestService.class);
		proxyFactory.setDefaultRequestChannel(requestChannel);
		proxyFactory.afterPropertiesSet();
		TestService service = (TestService) proxyFactory.getObject();
		service.oneWay("test");
		Message<?> message = requestChannel.receive(1000);
		assertNotNull(message);
		assertEquals("test", message.getPayload());
	}

	@Test
	public void testSolicitResponse() throws Exception {
		QueueChannel replyChannel = new QueueChannel();
		replyChannel.send(new StringMessage("foo"));
		GatewayProxyFactoryBean proxyFactory = new GatewayProxyFactoryBean();
		proxyFactory.setServiceInterface(TestService.class);
		proxyFactory.setDefaultRequestChannel(new DirectChannel());
		proxyFactory.setDefaultReplyChannel(replyChannel);
		proxyFactory.afterPropertiesSet();
		TestService service = (TestService) proxyFactory.getObject();
		String result = service.solicitResponse();
		assertNotNull(result);
		assertEquals("foo", result);
	}

	@Test
	public void testRequestReplyWithTypeConversion() throws Exception {
		final QueueChannel requestChannel = new QueueChannel();
		new Thread(new Runnable() {
			public void run() {
				Message<?> input = requestChannel.receive();
				StringMessage response = new StringMessage(input.getPayload() + "456");
				((MessageChannel) input.getHeaders().getReturnAddress()).send(response);
			}
		}).start();
		GatewayProxyFactoryBean proxyFactory = new GatewayProxyFactoryBean();
		proxyFactory.setServiceInterface(TestService.class);
		proxyFactory.setDefaultRequestChannel(requestChannel);
		proxyFactory.afterPropertiesSet();
		TestService service = (TestService) proxyFactory.getObject();
		Integer result = service.requestReplyWithIntegers(123);
		assertEquals(new Integer(123456), result);
	}

	@Test
	public void testRequestReplyWithRendezvousChannelInApplicationContext() {
		ClassPathXmlApplicationContext context = new ClassPathXmlApplicationContext(
				"gatewayWithRendezvousChannel.xml", GatewayProxyFactoryBeanTests.class);
		TestService service = (TestService) context.getBean("proxy");
		String result = service.requestReply("foo");
		assertEquals("foo!!!", result);
	}

	@Test
	public void testRequestReplyWithResponseCorrelatorInApplicationContext() {
		ClassPathXmlApplicationContext context = new ClassPathXmlApplicationContext(
				"gatewayWithResponseCorrelator.xml", GatewayProxyFactoryBeanTests.class);
		TestService service = (TestService) context.getBean("proxy");
		String result = service.requestReply("foo");
		assertEquals("foo!!!", result);
		TestChannelInterceptor interceptor = (TestChannelInterceptor) context.getBean("interceptor");
		assertEquals(1, interceptor.getSentCount());
		assertEquals(1, interceptor.getReceivedCount());
	}

	@Test
	public void testMultipleMessagesWithResponseCorrelator() throws InterruptedException {
		ClassPathXmlApplicationContext context = new ClassPathXmlApplicationContext(
				"gatewayWithResponseCorrelator.xml", GatewayProxyFactoryBeanTests.class);
		int numRequests = 500;
		final TestService service = (TestService) context.getBean("proxy");
		final String[] results = new String[numRequests];
		final CountDownLatch latch = new CountDownLatch(numRequests);
		Executor executor = Executors.newFixedThreadPool(numRequests);
		for (int i = 0; i < numRequests; i++) {
			final int count = i;
			executor.execute(new Runnable() {
				public void run() {
					// add some randomness to the ordering of requests
					try {
						Thread.sleep(new Random().nextInt(100));
					}
					catch (InterruptedException e) {
						// ignore
					}
					results[count] = service.requestReply("test-" + count);
					latch.countDown();
				}
			});
		}
		latch.await(10, TimeUnit.SECONDS);
		for (int i = 0; i < numRequests; i++) {
			assertEquals("test-" + i + "!!!", results[i]);
		}
		TestChannelInterceptor interceptor = (TestChannelInterceptor) context.getBean("interceptor");
		assertEquals(numRequests, interceptor.getSentCount());
		assertEquals(numRequests, interceptor.getReceivedCount());
	}

	@Test
	public void testMessageAsMethodArgument() throws Exception {
		QueueChannel requestChannel = new QueueChannel();
		startResponder(requestChannel);
		GatewayProxyFactoryBean proxyFactory = new GatewayProxyFactoryBean();
		proxyFactory.setServiceInterface(TestService.class);
		proxyFactory.setDefaultRequestChannel(requestChannel);
		proxyFactory.afterPropertiesSet();
		TestService service = (TestService) proxyFactory.getObject();
		String result = service.requestReplyWithMessageParameter(new StringMessage("foo"));
		assertEquals("foobar", result);
	}

	@Test
	public void testMessageAsReturnValue() throws Exception {
		final QueueChannel requestChannel = new QueueChannel();
		new Thread(new Runnable() {
			public void run() {
				Message<?> input = requestChannel.receive();
				StringMessage response = new StringMessage(input.getPayload() + "bar");
				((MessageChannel) input.getHeaders().getReturnAddress()).send(response);
			}
		}).start();
		GatewayProxyFactoryBean proxyFactory = new GatewayProxyFactoryBean();
		proxyFactory.setServiceInterface(TestService.class);
		proxyFactory.setDefaultRequestChannel(requestChannel);
		proxyFactory.afterPropertiesSet();
		TestService service = (TestService) proxyFactory.getObject();
		Message<?> result = service.requestReplyWithMessageReturnValue("foo");
		assertEquals("foobar", result.getPayload());
	}

	@Test
	public void testServiceMustBeInterface() {
		GatewayProxyFactoryBean proxyFactory = new GatewayProxyFactoryBean();
		int count = 0;
		try {
			proxyFactory.setServiceInterface(TestService.class);
			count++;
			proxyFactory.setServiceInterface(String.class);
			count++;
		}
		catch (IllegalArgumentException e) {
			// expected
		}
		assertEquals(1, count);
	}

	@Test
	public void testProxiedToStringMethod() throws Exception {
		GatewayProxyFactoryBean proxyFactory = new GatewayProxyFactoryBean();
		proxyFactory.setDefaultRequestChannel(new DirectChannel());
		proxyFactory.setServiceInterface(TestService.class);
		proxyFactory.afterPropertiesSet();
		Object proxy = proxyFactory.getObject();
		String expected = "gateway proxy for";
		assertEquals(expected, proxy.toString().substring(0, expected.length()));
	}


	private static void startResponder(final PollableChannel requestChannel) {
		new Thread(new Runnable() {
			public void run() {
				Message<?> input = requestChannel.receive();
				StringMessage response = new StringMessage(input.getPayload() + "bar");
				((MessageChannel) input.getHeaders().getReturnAddress()).send(response);
			}
		}).start();
	}

}
