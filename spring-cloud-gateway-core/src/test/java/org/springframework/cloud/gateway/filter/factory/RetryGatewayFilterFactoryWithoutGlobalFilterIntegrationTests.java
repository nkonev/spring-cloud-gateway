/*
 * Copyright 2013-2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.gateway.filter.factory;

import com.netflix.loadbalancer.Server;
import com.netflix.loadbalancer.ServerList;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hamcrest.CoreMatchers;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.rule.OutputCapture;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.RetryGatewayFilterFactory.RetryConfig;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.cloud.gateway.test.BaseWebClientTests;
import org.springframework.cloud.netflix.ribbon.RibbonClient;
import org.springframework.cloud.netflix.ribbon.StaticServerList;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = RANDOM_PORT/*
											 * , properties =
											 * {"spring.cloud.gateway.default-filters="}
											 */)
@DirtiesContext
@ActiveProfiles(profiles = "no-global-filter")
@TestConfiguration(value = "application-no-global-filter.yml")
public class RetryGatewayFilterFactoryWithoutGlobalFilterIntegrationTests
		extends BaseWebClientTests {

	private static final String HELLO_GATEWAY = "HelloGateway";

	@Rule
	public final OutputCapture capture = new OutputCapture();

	@Before
	public void before() {
		capture.reset();
	}

	@Test
	public void retryFilterPostOneTime() throws InterruptedException {
		testClient.post().uri("/httpbin/regular-post")
				.header(HttpHeaders.HOST, "www.retrypostconfig.org")
				.syncBody(HELLO_GATEWAY).exchange().expectStatus().isOk()
				.expectBody(String.class).isEqualTo(HELLO_GATEWAY);
		assertThat(this.capture.toString()).contains("setting new iteration in attr 0");
		assertThat(this.capture.toString())
				.doesNotContain("setting new iteration in attr 1");
	}

	@RestController
	@EnableAutoConfiguration
	@SpringBootConfiguration
	@Import(DefaultTestConfig.class)
	@RibbonClient(name = TestConfig.BADSERVICE_3, configuration = TestBadRibbonConfig.class)
	public static class TestConfig {

		private static final String BADSERVICE_3 = "badservice3";

		@Value("${test.uri}")
		private String uri;

		@RequestMapping("/httpbin/regular-post")
		public ResponseEntity<String> retry(@RequestBody String body) {
			return ResponseEntity.status(HttpStatus.OK).body(body);
		}

		@Bean
		public RouteLocator hystrixRouteLocator(RouteLocatorBuilder builder) {
			return builder.routes()
					.route("retry_with_loadbalancer",
							r -> r.host("**.retrywithloadbalancer.org")
									.filters(f -> f.prefixPath("/httpbin")
											.retry(config -> config.setRetries(2)))
									.uri("lb://" + BADSERVICE_3))
					.build();
		}

	}

	protected static class TestBadRibbonConfig {

		@LocalServerPort
		protected int port = 0;

		@Bean
		public ServerList<Server> ribbonServerList() {
			return new StaticServerList<>(
					new Server("https", "localhost.domain.doesnot.exist", this.port),
					new Server("localhost", this.port));
		}

	}

}
