/*
 * Copyright 2002-2023 the original author or authors.
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

package org.springframework.security.config.annotation.web.configuration;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.oauth2.client.CommonOAuth2Provider;
import org.springframework.security.config.test.SpringTestContext;
import org.springframework.security.oauth2.client.AuthorizationCodeOAuth2AuthorizedClientProvider;
import org.springframework.security.oauth2.client.ClientAuthorizationRequiredException;
import org.springframework.security.oauth2.client.ClientCredentialsOAuth2AuthorizedClientProvider;
import org.springframework.security.oauth2.client.JwtBearerOAuth2AuthorizedClientProvider;
import org.springframework.security.oauth2.client.OAuth2AuthorizationContext;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.PasswordOAuth2AuthorizedClientProvider;
import org.springframework.security.oauth2.client.RefreshTokenOAuth2AuthorizedClientProvider;
import org.springframework.security.oauth2.client.TokenExchangeOAuth2AuthorizedClientProvider;
import org.springframework.security.oauth2.client.endpoint.AbstractOAuth2AuthorizationGrantRequest;
import org.springframework.security.oauth2.client.endpoint.JwtBearerGrantRequest;
import org.springframework.security.oauth2.client.endpoint.OAuth2AccessTokenResponseClient;
import org.springframework.security.oauth2.client.endpoint.OAuth2AuthorizationCodeGrantRequest;
import org.springframework.security.oauth2.client.endpoint.OAuth2ClientCredentialsGrantRequest;
import org.springframework.security.oauth2.client.endpoint.OAuth2PasswordGrantRequest;
import org.springframework.security.oauth2.client.endpoint.OAuth2RefreshTokenGrantRequest;
import org.springframework.security.oauth2.client.endpoint.TokenExchangeGrantRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AuthorizationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.TestOAuth2RefreshTokens;
import org.springframework.security.oauth2.core.endpoint.OAuth2AccessTokenResponse;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.core.endpoint.TestOAuth2AccessTokenResponses;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.oauth2.jwt.JoseHeaderNames;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.util.StringUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

/**
 * Tests for {@link OAuth2ClientConfiguration.OAuth2AuthorizedClientManagerConfiguration}.
 *
 * @author Joe Grandja
 * @author Steve Riesenberg
 */
public class OAuth2AuthorizedClientManagerConfigurationTests {

	private static OAuth2AccessTokenResponseClient<? super AbstractOAuth2AuthorizationGrantRequest> MOCK_RESPONSE_CLIENT;

	public final SpringTestContext spring = new SpringTestContext(this);

	@Autowired
	private OAuth2AuthorizedClientManager authorizedClientManager;

	@Autowired
	private ClientRegistrationRepository clientRegistrationRepository;

	@Autowired
	private OAuth2AuthorizedClientRepository authorizedClientRepository;

	@Autowired(required = false)
	private AuthorizationCodeOAuth2AuthorizedClientProvider authorizationCodeAuthorizedClientProvider;

	private MockHttpServletRequest request;

	private MockHttpServletResponse response;

	@BeforeEach
	@SuppressWarnings("unchecked")
	public void setUp() {
		MOCK_RESPONSE_CLIENT = mock(OAuth2AccessTokenResponseClient.class);
		this.request = new MockHttpServletRequest();
		this.response = new MockHttpServletResponse();
	}

	@Test
	public void loadContextWhenOAuth2ClientEnabledThenConfigured() {
		this.spring.register(MinimalOAuth2ClientConfig.class).autowire();
		assertThat(this.authorizedClientManager).isNotNull();
	}

	@Test
	public void authorizeWhenAuthorizationCodeAuthorizedClientProviderBeanThenUsed() {
		this.spring.register(CustomAuthorizedClientProvidersConfig.class).autowire();

		TestingAuthenticationToken authentication = new TestingAuthenticationToken("user", null);
		// @formatter:off
		OAuth2AuthorizeRequest authorizeRequest = OAuth2AuthorizeRequest
				.withClientRegistrationId("google")
				.principal(authentication)
				.attribute(HttpServletRequest.class.getName(), this.request)
				.attribute(HttpServletResponse.class.getName(), this.response)
				.build();
		assertThatExceptionOfType(ClientAuthorizationRequiredException.class)
				.isThrownBy(() -> this.authorizedClientManager.authorize(authorizeRequest))
				.extracting(OAuth2AuthorizationException::getError)
				.extracting(OAuth2Error::getErrorCode)
				.isEqualTo("client_authorization_required");
		// @formatter:on

		verify(this.authorizationCodeAuthorizedClientProvider).authorize(any(OAuth2AuthorizationContext.class));
	}

	@Test
	public void authorizeWhenRefreshTokenAccessTokenResponseClientBeanThenUsed() {
		this.spring.register(CustomAccessTokenResponseClientsConfig.class).autowire();
		testRefreshTokenGrant();
	}

	@Test
	public void authorizeWhenRefreshTokenAuthorizedClientProviderBeanThenUsed() {
		this.spring.register(CustomAuthorizedClientProvidersConfig.class).autowire();
		testRefreshTokenGrant();
	}

	private void testRefreshTokenGrant() {
		OAuth2AccessTokenResponse accessTokenResponse = TestOAuth2AccessTokenResponses.accessTokenResponse().build();
		given(MOCK_RESPONSE_CLIENT.getTokenResponse(any(OAuth2RefreshTokenGrantRequest.class)))
			.willReturn(accessTokenResponse);

		TestingAuthenticationToken authentication = new TestingAuthenticationToken("user", null);
		ClientRegistration clientRegistration = this.clientRegistrationRepository.findByRegistrationId("google");
		OAuth2AuthorizedClient existingAuthorizedClient = new OAuth2AuthorizedClient(clientRegistration,
				authentication.getName(), getExpiredAccessToken(), TestOAuth2RefreshTokens.refreshToken());
		this.authorizedClientRepository.saveAuthorizedClient(existingAuthorizedClient, authentication, this.request,
				this.response);
		// @formatter:off
		OAuth2AuthorizeRequest authorizeRequest = OAuth2AuthorizeRequest
				.withAuthorizedClient(existingAuthorizedClient)
				.principal(authentication)
				.attribute(HttpServletRequest.class.getName(), this.request)
				.attribute(HttpServletResponse.class.getName(), this.response)
				.build();
		// @formatter:on
		OAuth2AuthorizedClient authorizedClient = this.authorizedClientManager.authorize(authorizeRequest);
		assertThat(authorizedClient).isNotNull();

		ArgumentCaptor<OAuth2RefreshTokenGrantRequest> grantRequestCaptor = ArgumentCaptor
			.forClass(OAuth2RefreshTokenGrantRequest.class);
		verify(MOCK_RESPONSE_CLIENT).getTokenResponse(grantRequestCaptor.capture());

		OAuth2RefreshTokenGrantRequest grantRequest = grantRequestCaptor.getValue();
		assertThat(grantRequest.getClientRegistration().getRegistrationId())
			.isEqualTo(clientRegistration.getRegistrationId());
		assertThat(grantRequest.getGrantType()).isEqualTo(AuthorizationGrantType.REFRESH_TOKEN);
		assertThat(grantRequest.getAccessToken()).isEqualTo(existingAuthorizedClient.getAccessToken());
		assertThat(grantRequest.getRefreshToken()).isEqualTo(existingAuthorizedClient.getRefreshToken());
	}

	@Test
	public void authorizeWhenClientCredentialsAccessTokenResponseClientBeanThenUsed() {
		this.spring.register(CustomAccessTokenResponseClientsConfig.class).autowire();
		testClientCredentialsGrant();
	}

	@Test
	public void authorizeWhenClientCredentialsAuthorizedClientProviderBeanThenUsed() {
		this.spring.register(CustomAuthorizedClientProvidersConfig.class).autowire();
		testClientCredentialsGrant();
	}

	private void testClientCredentialsGrant() {
		OAuth2AccessTokenResponse accessTokenResponse = TestOAuth2AccessTokenResponses.accessTokenResponse().build();
		given(MOCK_RESPONSE_CLIENT.getTokenResponse(any(OAuth2ClientCredentialsGrantRequest.class)))
			.willReturn(accessTokenResponse);

		TestingAuthenticationToken authentication = new TestingAuthenticationToken("user", null);
		ClientRegistration clientRegistration = this.clientRegistrationRepository.findByRegistrationId("github");
		// @formatter:off
		OAuth2AuthorizeRequest authorizeRequest = OAuth2AuthorizeRequest
				.withClientRegistrationId(clientRegistration.getRegistrationId())
				.principal(authentication)
				.attribute(HttpServletRequest.class.getName(), this.request)
				.attribute(HttpServletResponse.class.getName(), this.response)
				.build();
		// @formatter:on
		OAuth2AuthorizedClient authorizedClient = this.authorizedClientManager.authorize(authorizeRequest);
		assertThat(authorizedClient).isNotNull();

		ArgumentCaptor<OAuth2ClientCredentialsGrantRequest> grantRequestCaptor = ArgumentCaptor
			.forClass(OAuth2ClientCredentialsGrantRequest.class);
		verify(MOCK_RESPONSE_CLIENT).getTokenResponse(grantRequestCaptor.capture());

		OAuth2ClientCredentialsGrantRequest grantRequest = grantRequestCaptor.getValue();
		assertThat(grantRequest.getClientRegistration().getRegistrationId())
			.isEqualTo(clientRegistration.getRegistrationId());
		assertThat(grantRequest.getGrantType()).isEqualTo(AuthorizationGrantType.CLIENT_CREDENTIALS);
	}

	@Test
	public void authorizeWhenPasswordAccessTokenResponseClientBeanThenUsed() {
		this.spring.register(CustomAccessTokenResponseClientsConfig.class).autowire();
		testPasswordGrant();
	}

	@Test
	public void authorizeWhenPasswordAuthorizedClientProviderBeanThenUsed() {
		this.spring.register(CustomAuthorizedClientProvidersConfig.class).autowire();
		testPasswordGrant();
	}

	private void testPasswordGrant() {
		OAuth2AccessTokenResponse accessTokenResponse = TestOAuth2AccessTokenResponses.accessTokenResponse().build();
		given(MOCK_RESPONSE_CLIENT.getTokenResponse(any(OAuth2PasswordGrantRequest.class)))
			.willReturn(accessTokenResponse);

		TestingAuthenticationToken authentication = new TestingAuthenticationToken("user", "password");
		ClientRegistration clientRegistration = this.clientRegistrationRepository.findByRegistrationId("facebook");
		// @formatter:off
		OAuth2AuthorizeRequest authorizeRequest = OAuth2AuthorizeRequest
				.withClientRegistrationId(clientRegistration.getRegistrationId())
				.principal(authentication)
				.attribute(HttpServletRequest.class.getName(), this.request)
				.attribute(HttpServletResponse.class.getName(), this.response)
				.build();
		// @formatter:on
		this.request.setParameter(OAuth2ParameterNames.USERNAME, "user");
		this.request.setParameter(OAuth2ParameterNames.PASSWORD, "password");
		OAuth2AuthorizedClient authorizedClient = this.authorizedClientManager.authorize(authorizeRequest);
		assertThat(authorizedClient).isNotNull();

		ArgumentCaptor<OAuth2PasswordGrantRequest> grantRequestCaptor = ArgumentCaptor
			.forClass(OAuth2PasswordGrantRequest.class);
		verify(MOCK_RESPONSE_CLIENT).getTokenResponse(grantRequestCaptor.capture());

		OAuth2PasswordGrantRequest grantRequest = grantRequestCaptor.getValue();
		assertThat(grantRequest.getClientRegistration().getRegistrationId())
			.isEqualTo(clientRegistration.getRegistrationId());
		assertThat(grantRequest.getGrantType()).isEqualTo(AuthorizationGrantType.PASSWORD);
		assertThat(grantRequest.getUsername()).isEqualTo("user");
		assertThat(grantRequest.getPassword()).isEqualTo("password");
	}

	@Test
	public void authorizeWhenJwtBearerAccessTokenResponseClientBeanThenUsed() {
		this.spring.register(CustomAccessTokenResponseClientsConfig.class).autowire();
		testJwtBearerGrant();
	}

	@Test
	public void authorizeWhenJwtBearerAuthorizedClientProviderBeanThenUsed() {
		this.spring.register(CustomAuthorizedClientProvidersConfig.class).autowire();
		testJwtBearerGrant();
	}

	private void testJwtBearerGrant() {
		OAuth2AccessTokenResponse accessTokenResponse = TestOAuth2AccessTokenResponses.accessTokenResponse().build();
		given(MOCK_RESPONSE_CLIENT.getTokenResponse(any(JwtBearerGrantRequest.class))).willReturn(accessTokenResponse);

		JwtAuthenticationToken authentication = new JwtAuthenticationToken(getJwt());
		ClientRegistration clientRegistration = this.clientRegistrationRepository.findByRegistrationId("okta");
		// @formatter:off
		OAuth2AuthorizeRequest authorizeRequest = OAuth2AuthorizeRequest
				.withClientRegistrationId(clientRegistration.getRegistrationId())
				.principal(authentication)
				.attribute(HttpServletRequest.class.getName(), this.request)
				.attribute(HttpServletResponse.class.getName(), this.response)
				.build();
		// @formatter:on
		OAuth2AuthorizedClient authorizedClient = this.authorizedClientManager.authorize(authorizeRequest);
		assertThat(authorizedClient).isNotNull();

		ArgumentCaptor<JwtBearerGrantRequest> grantRequestCaptor = ArgumentCaptor.forClass(JwtBearerGrantRequest.class);
		verify(MOCK_RESPONSE_CLIENT).getTokenResponse(grantRequestCaptor.capture());

		JwtBearerGrantRequest grantRequest = grantRequestCaptor.getValue();
		assertThat(grantRequest.getClientRegistration().getRegistrationId())
			.isEqualTo(clientRegistration.getRegistrationId());
		assertThat(grantRequest.getGrantType()).isEqualTo(AuthorizationGrantType.JWT_BEARER);
		assertThat(grantRequest.getJwt().getSubject()).isEqualTo("user");
	}

	@Test
	public void authorizeWhenTokenExchangeAccessTokenResponseClientBeanThenUsed() {
		this.spring.register(CustomAccessTokenResponseClientsConfig.class).autowire();
		testTokenExchangeGrant();
	}

	@Test
	public void authorizeWhenTokenExchangeAuthorizedClientProviderBeanThenUsed() {
		this.spring.register(CustomAuthorizedClientProvidersConfig.class).autowire();
		testTokenExchangeGrant();
	}

	private void testTokenExchangeGrant() {
		OAuth2AccessTokenResponse accessTokenResponse = TestOAuth2AccessTokenResponses.accessTokenResponse().build();
		given(MOCK_RESPONSE_CLIENT.getTokenResponse(any(TokenExchangeGrantRequest.class)))
			.willReturn(accessTokenResponse);

		JwtAuthenticationToken authentication = new JwtAuthenticationToken(getJwt());
		ClientRegistration clientRegistration = this.clientRegistrationRepository.findByRegistrationId("auth0");
		// @formatter:off
		OAuth2AuthorizeRequest authorizeRequest = OAuth2AuthorizeRequest
				.withClientRegistrationId(clientRegistration.getRegistrationId())
				.principal(authentication)
				.attribute(HttpServletRequest.class.getName(), this.request)
				.attribute(HttpServletResponse.class.getName(), this.response)
				.build();
		// @formatter:on
		OAuth2AuthorizedClient authorizedClient = this.authorizedClientManager.authorize(authorizeRequest);
		assertThat(authorizedClient).isNotNull();

		ArgumentCaptor<TokenExchangeGrantRequest> grantRequestCaptor = ArgumentCaptor
			.forClass(TokenExchangeGrantRequest.class);
		verify(MOCK_RESPONSE_CLIENT).getTokenResponse(grantRequestCaptor.capture());

		TokenExchangeGrantRequest grantRequest = grantRequestCaptor.getValue();
		assertThat(grantRequest.getClientRegistration().getRegistrationId())
			.isEqualTo(clientRegistration.getRegistrationId());
		assertThat(grantRequest.getGrantType()).isEqualTo(AuthorizationGrantType.TOKEN_EXCHANGE);
		assertThat(grantRequest.getSubjectToken()).isEqualTo(authentication.getToken());
	}

	private static OAuth2AccessToken getExpiredAccessToken() {
		Instant expiresAt = Instant.now().minusSeconds(60);
		Instant issuedAt = expiresAt.minus(Duration.ofDays(1));
		return new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, "scopes", issuedAt, expiresAt,
				new HashSet<>(Arrays.asList("read", "write")));
	}

	private static Jwt getJwt() {
		Instant issuedAt = Instant.now();
		return new Jwt("token", issuedAt, issuedAt.plusSeconds(300),
				Collections.singletonMap(JoseHeaderNames.ALG, "RS256"),
				Collections.singletonMap(JwtClaimNames.SUB, "user"));
	}

	@Configuration
	@EnableWebSecurity
	static class MinimalOAuth2ClientConfig extends OAuth2ClientBaseConfig {

	}

	@Configuration
	@EnableWebSecurity
	static class CustomAccessTokenResponseClientsConfig extends OAuth2ClientBaseConfig {

		@Bean
		OAuth2AccessTokenResponseClient<OAuth2AuthorizationCodeGrantRequest> authorizationCodeTokenResponseClient() {
			return new MockAuthorizationCodeClient();
		}

		@Bean
		OAuth2AccessTokenResponseClient<OAuth2RefreshTokenGrantRequest> refreshTokenTokenResponseClient() {
			return new MockRefreshTokenClient();
		}

		@Bean
		OAuth2AccessTokenResponseClient<OAuth2ClientCredentialsGrantRequest> clientCredentialsTokenResponseClient() {
			return new MockClientCredentialsClient();
		}

		@Bean
		OAuth2AccessTokenResponseClient<OAuth2PasswordGrantRequest> passwordTokenResponseClient() {
			return new MockPasswordClient();
		}

		@Bean
		OAuth2AccessTokenResponseClient<JwtBearerGrantRequest> jwtBearerTokenResponseClient() {
			return new MockJwtBearerClient();
		}

		@Bean
		OAuth2AccessTokenResponseClient<TokenExchangeGrantRequest> tokenExchangeTokenResponseClient() {
			return new MockTokenExchangeClient();
		}

		@Bean
		OAuth2UserService<OAuth2UserRequest, OAuth2User> oauth2UserService() {
			return mock(DefaultOAuth2UserService.class);
		}

		@Bean
		OAuth2UserService<OidcUserRequest, OidcUser> oidcUserService() {
			return mock(OidcUserService.class);
		}

	}

	@Configuration
	@EnableWebSecurity
	static class CustomAuthorizedClientProvidersConfig extends OAuth2ClientBaseConfig {

		@Bean
		AuthorizationCodeOAuth2AuthorizedClientProvider authorizationCodeProvider() {
			return spy(new AuthorizationCodeOAuth2AuthorizedClientProvider());
		}

		@Bean
		RefreshTokenOAuth2AuthorizedClientProvider refreshTokenProvider() {
			RefreshTokenOAuth2AuthorizedClientProvider authorizedClientProvider = new RefreshTokenOAuth2AuthorizedClientProvider();
			authorizedClientProvider.setAccessTokenResponseClient(new MockRefreshTokenClient());
			return authorizedClientProvider;
		}

		@Bean
		ClientCredentialsOAuth2AuthorizedClientProvider clientCredentialsProvider() {
			ClientCredentialsOAuth2AuthorizedClientProvider authorizedClientProvider = new ClientCredentialsOAuth2AuthorizedClientProvider();
			authorizedClientProvider.setAccessTokenResponseClient(new MockClientCredentialsClient());
			return authorizedClientProvider;
		}

		@Bean
		PasswordOAuth2AuthorizedClientProvider passwordProvider() {
			PasswordOAuth2AuthorizedClientProvider authorizedClientProvider = new PasswordOAuth2AuthorizedClientProvider();
			authorizedClientProvider.setAccessTokenResponseClient(new MockPasswordClient());
			return authorizedClientProvider;
		}

		@Bean
		JwtBearerOAuth2AuthorizedClientProvider jwtBearerAuthorizedClientProvider() {
			JwtBearerOAuth2AuthorizedClientProvider authorizedClientProvider = new JwtBearerOAuth2AuthorizedClientProvider();
			authorizedClientProvider.setAccessTokenResponseClient(new MockJwtBearerClient());
			return authorizedClientProvider;
		}

		@Bean
		TokenExchangeOAuth2AuthorizedClientProvider tokenExchangeAuthorizedClientProvider() {
			TokenExchangeOAuth2AuthorizedClientProvider authorizedClientProvider = new TokenExchangeOAuth2AuthorizedClientProvider();
			authorizedClientProvider.setAccessTokenResponseClient(new MockTokenExchangeClient());
			return authorizedClientProvider;
		}

	}

	abstract static class OAuth2ClientBaseConfig {

		@Bean
		SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
			// @formatter:off
			http
				.authorizeHttpRequests((authorize) -> authorize.anyRequest().authenticated())
				.oauth2Login(Customizer.withDefaults())
				.oauth2Client(Customizer.withDefaults());
			return http.build();
			// @formatter:on
		}

		@Bean
		ClientRegistrationRepository clientRegistrationRepository() {
			// @formatter:off
			return new InMemoryClientRegistrationRepository(Arrays.asList(
					CommonOAuth2Provider.GOOGLE.getBuilder("google")
							.clientId("google-client-id")
							.clientSecret("google-client-secret")
							.authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
							.build(),
					CommonOAuth2Provider.GITHUB.getBuilder("github")
							.clientId("github-client-id")
							.clientSecret("github-client-secret")
							.authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
							.build(),
					CommonOAuth2Provider.FACEBOOK.getBuilder("facebook")
							.clientId("facebook-client-id")
							.clientSecret("facebook-client-secret")
							.authorizationGrantType(AuthorizationGrantType.PASSWORD)
							.build(),
					CommonOAuth2Provider.OKTA.getBuilder("okta")
							.clientId("okta-client-id")
							.clientSecret("okta-client-secret")
							.authorizationGrantType(AuthorizationGrantType.JWT_BEARER)
							.build(),
					ClientRegistration.withRegistrationId("auth0")
							.clientName("Auth0")
							.clientId("auth0-client-id")
							.clientSecret("auth0-client-secret")
							.clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
							.authorizationGrantType(AuthorizationGrantType.TOKEN_EXCHANGE)
							.scope("user.read", "user.write")
							.build()));
			// @formatter:on
		}

		@Bean
		OAuth2AuthorizedClientRepository authorizedClientRepository() {
			return mock(OAuth2AuthorizedClientRepository.class);
		}

		@Bean
		Consumer<DefaultOAuth2AuthorizedClientManager> authorizedClientManagerConsumer() {
			return (authorizedClientManager) -> authorizedClientManager
				.setContextAttributesMapper((authorizeRequest) -> {
					HttpServletRequest request = Objects
						.requireNonNull(authorizeRequest.getAttribute(HttpServletRequest.class.getName()));
					String username = request.getParameter(OAuth2ParameterNames.USERNAME);
					String password = request.getParameter(OAuth2ParameterNames.PASSWORD);

					Map<String, Object> attributes = Collections.emptyMap();
					if (StringUtils.hasText(username) && StringUtils.hasText(password)) {
						attributes = new HashMap<>();
						attributes.put(OAuth2AuthorizationContext.USERNAME_ATTRIBUTE_NAME, username);
						attributes.put(OAuth2AuthorizationContext.PASSWORD_ATTRIBUTE_NAME, password);
					}

					return attributes;
				});
		}

	}

	private static class MockAuthorizationCodeClient
			implements OAuth2AccessTokenResponseClient<OAuth2AuthorizationCodeGrantRequest> {

		@Override
		public OAuth2AccessTokenResponse getTokenResponse(
				OAuth2AuthorizationCodeGrantRequest authorizationGrantRequest) {
			return MOCK_RESPONSE_CLIENT.getTokenResponse(authorizationGrantRequest);
		}

	}

	private static class MockRefreshTokenClient
			implements OAuth2AccessTokenResponseClient<OAuth2RefreshTokenGrantRequest> {

		@Override
		public OAuth2AccessTokenResponse getTokenResponse(OAuth2RefreshTokenGrantRequest authorizationGrantRequest) {
			return MOCK_RESPONSE_CLIENT.getTokenResponse(authorizationGrantRequest);
		}

	}

	private static class MockClientCredentialsClient
			implements OAuth2AccessTokenResponseClient<OAuth2ClientCredentialsGrantRequest> {

		@Override
		public OAuth2AccessTokenResponse getTokenResponse(
				OAuth2ClientCredentialsGrantRequest authorizationGrantRequest) {
			return MOCK_RESPONSE_CLIENT.getTokenResponse(authorizationGrantRequest);
		}

	}

	private static class MockPasswordClient implements OAuth2AccessTokenResponseClient<OAuth2PasswordGrantRequest> {

		@Override
		public OAuth2AccessTokenResponse getTokenResponse(OAuth2PasswordGrantRequest authorizationGrantRequest) {
			return MOCK_RESPONSE_CLIENT.getTokenResponse(authorizationGrantRequest);
		}

	}

	private static class MockJwtBearerClient implements OAuth2AccessTokenResponseClient<JwtBearerGrantRequest> {

		@Override
		public OAuth2AccessTokenResponse getTokenResponse(JwtBearerGrantRequest authorizationGrantRequest) {
			return MOCK_RESPONSE_CLIENT.getTokenResponse(authorizationGrantRequest);
		}

	}

	private static class MockTokenExchangeClient implements OAuth2AccessTokenResponseClient<TokenExchangeGrantRequest> {

		@Override
		public OAuth2AccessTokenResponse getTokenResponse(TokenExchangeGrantRequest authorizationGrantRequest) {
			return MOCK_RESPONSE_CLIENT.getTokenResponse(authorizationGrantRequest);
		}

	}

}
