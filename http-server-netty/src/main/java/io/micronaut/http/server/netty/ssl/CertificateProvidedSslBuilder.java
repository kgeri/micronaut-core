/*
 * Copyright 2017-2022 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.http.server.netty.ssl;

import io.micronaut.context.annotation.Requires;
import io.micronaut.context.condition.ConditionContext;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.io.ResourceResolver;
import io.micronaut.core.order.Ordered;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.http.HttpVersion;
import io.micronaut.http.server.HttpServerConfiguration;
import io.micronaut.http.ssl.ClientAuthentication;
import io.micronaut.http.ssl.ServerSslConfiguration;
import io.micronaut.http.ssl.SslBuilder;
import io.micronaut.http.ssl.SslConfiguration;
import io.micronaut.http.ssl.SslConfigurationException;
import io.micronaut.runtime.context.scope.refresh.RefreshEvent;
import io.micronaut.runtime.context.scope.refresh.RefreshEventListener;
import io.netty.handler.codec.http2.Http2SecurityUtil;
import io.netty.handler.ssl.ApplicationProtocolConfig;
import io.netty.handler.ssl.ApplicationProtocolNames;
import io.netty.handler.ssl.ClientAuth;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslProvider;
import io.netty.handler.ssl.SupportedCipherSuiteFilter;
import jakarta.inject.Singleton;

import javax.net.ssl.SSLException;
import java.security.KeyStore;
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;

/**
 * The Netty implementation of {@link SslBuilder} that generates an {@link SslContext} to create a server handle with
 * SSL support via user configuration.
 */
@Requires(condition = SslEnabledCondition.class)
@Requires(condition = CertificateProvidedSslBuilder.SelfSignedNotConfigured.class)
@Singleton
@Internal
public class CertificateProvidedSslBuilder extends SslBuilder<SslContext> implements ServerSslBuilder, RefreshEventListener, Ordered {

    private final ServerSslConfiguration ssl;
    private final HttpServerConfiguration httpServerConfiguration;
    private KeyStore keyStoreCache = null;
    private KeyStore trustStoreCache = null;

    /**
     * @param httpServerConfiguration The HTTP server configuration
     * @param ssl                     The ssl configuration
     * @param resourceResolver        The resource resolver
     */
    public CertificateProvidedSslBuilder(
            HttpServerConfiguration httpServerConfiguration,
            ServerSslConfiguration ssl,
            ResourceResolver resourceResolver) {
        super(resourceResolver);
        this.ssl = ssl;
        this.httpServerConfiguration = httpServerConfiguration;
    }

    @Override
    public ServerSslConfiguration getSslConfiguration() {
        return ssl;
    }

    @Override
    public Optional<SslContext> build() {
        return build(ssl);
    }

    @SuppressWarnings("Duplicates")
    @Override
    public Optional<SslContext> build(SslConfiguration ssl) {
        final HttpVersion httpVersion = httpServerConfiguration.getHttpVersion();
        return build(ssl, httpVersion);
    }

    @Override
    public Optional<SslContext> build(SslConfiguration ssl, HttpVersion httpVersion) {
        SslContextBuilder sslBuilder = SslContextBuilder
                .forServer(getKeyManagerFactory(ssl))
                .trustManager(getTrustManagerFactory(ssl));

        setupSslBuilder(sslBuilder, ssl, httpVersion);
        try {
            return Optional.of(sslBuilder.build());
        } catch (SSLException ex) {
            throw new SslConfigurationException("An error occurred while setting up SSL", ex);
        }
    }

    static void setupSslBuilder(SslContextBuilder sslBuilder, SslConfiguration ssl, HttpVersion httpVersion) {
        Optional<String[]> protocols = ssl.getProtocols();
        if (protocols.isPresent()) {
            sslBuilder.protocols(protocols.get());
        }
        final boolean isHttp2 = httpVersion == HttpVersion.HTTP_2_0;
        Optional<String[]> ciphers = ssl.getCiphers();
        if (ciphers.isPresent()) {
            sslBuilder = sslBuilder.ciphers(Arrays.asList(ciphers.get()));
        } else if (isHttp2) {
            sslBuilder.ciphers(Http2SecurityUtil.CIPHERS, SupportedCipherSuiteFilter.INSTANCE);
        }
        Optional<ClientAuthentication> clientAuthentication = ssl.getClientAuthentication();
        if (clientAuthentication.isPresent()) {
            ClientAuthentication clientAuth = clientAuthentication.get();
            if (clientAuth == ClientAuthentication.NEED) {
                sslBuilder.clientAuth(ClientAuth.REQUIRE);
            } else if (clientAuth == ClientAuthentication.WANT) {
                sslBuilder.clientAuth(ClientAuth.OPTIONAL);
            }
        }

        if (isHttp2) {
            SslProvider provider = SslProvider.isAlpnSupported(SslProvider.OPENSSL) ? SslProvider.OPENSSL : SslProvider.JDK;
            sslBuilder.sslProvider(provider);
            sslBuilder.applicationProtocolConfig(new ApplicationProtocolConfig(
                    ApplicationProtocolConfig.Protocol.ALPN,
                    ApplicationProtocolConfig.SelectorFailureBehavior.NO_ADVERTISE,
                    ApplicationProtocolConfig.SelectedListenerFailureBehavior.ACCEPT,
                    ApplicationProtocolNames.HTTP_2,
                    ApplicationProtocolNames.HTTP_1_1
            ));
        }
    }

    @Override
    protected Optional<KeyStore> getTrustStore(SslConfiguration ssl) throws Exception {
        if (trustStoreCache == null) {
            super.getTrustStore(ssl).ifPresent(trustStore -> trustStoreCache = trustStore);
        }
        return Optional.ofNullable(trustStoreCache);
    }

    @Override
    protected Optional<KeyStore> getKeyStore(SslConfiguration ssl) throws Exception {
        if (keyStoreCache == null) {
            super.getKeyStore(ssl).ifPresent(keyStore -> keyStoreCache = keyStore);
        }
        return Optional.ofNullable(keyStoreCache);
    }

    @Override
    public Set<String> getObservedConfigurationPrefixes() {
        return CollectionUtils.setOf(
                SslConfiguration.PREFIX,
                ServerSslConfiguration.PREFIX
        );
    }

    @Override
    public void onApplicationEvent(RefreshEvent event) {
        // clear caches
        keyStoreCache = null;
        trustStoreCache = null;
    }

    @Override
    public int getOrder() {
        return RefreshEventListener.DEFAULT_POSITION - 10;
    }

    static class SelfSignedNotConfigured extends BuildSelfSignedCondition {
        @Override
        protected boolean validate(ConditionContext context, boolean deprecatedPropertyFound, boolean newPropertyFound) {
            if (deprecatedPropertyFound) {
                context.fail("Deprecated  " + SslConfiguration.PREFIX + ".build-self-signed config detected, disabling provided certificate.");
                return false;
            } else if (newPropertyFound) {
                context.fail(ServerSslConfiguration.PREFIX + ".build-self-signed config detected, disabling provided certificate.");
                return false;
            } else {
                return true;
            }
        }
    }
}
