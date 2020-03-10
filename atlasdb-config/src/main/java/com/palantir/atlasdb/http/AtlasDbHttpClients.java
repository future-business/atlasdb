/*
 * (c) Copyright 2018 Palantir Technologies Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.palantir.atlasdb.http;

import java.util.Optional;
import java.util.function.Supplier;

import com.codahale.metrics.MetricRegistry;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import com.palantir.atlasdb.config.AuxiliaryRemotingParameters;
import com.palantir.atlasdb.config.ServerListConfig;
import com.palantir.atlasdb.http.v2.ConjureJavaRuntimeTargetFactory;
import com.palantir.atlasdb.util.AtlasDbMetrics;
import com.palantir.atlasdb.util.MetricsManager;
import com.palantir.common.proxy.SelfRefreshingProxy;
import com.palantir.conjure.java.config.ssl.TrustContext;
import com.palantir.tritium.metrics.registry.TaggedMetricRegistry;

public final class AtlasDbHttpClients {

    private AtlasDbHttpClients() {
        // Utility class
    }

    public static <T> T createProxy(
            Optional<TrustContext> trustContext,
            String uri,
            Class<T> type,
            AuxiliaryRemotingParameters parameters) {
        return SelfRefreshingProxy.create(
                () -> ConjureJavaRuntimeTargetFactory.DEFAULT.createProxy(trustContext, uri, type, parameters)
                        .instance(),
                type);
    }

    /**
     * Constructs an HTTP-invoking dynamic proxy for the specified type that will cycle through the list of supplied
     * endpoints after encountering an exception or connection failure, using the supplied SSL factory if it is
     * present. Also use the supplied the proxy selector to set the proxy on the clients if present.
     * <p>
     * Failover will continue to cycle through the supplied endpoint list indefinitely.
     */
    public static <T> T createProxyWithFailover(
            ServerListConfig serverListConfig,
            Class<T> type,
            AuxiliaryRemotingParameters parameters) {
        Supplier<T> clientFactory = () ->
                ConjureJavaRuntimeTargetFactory.DEFAULT
                        .createProxyWithFailover(serverListConfig, type, parameters)
                        .instance();
        return SelfRefreshingProxy.create(clientFactory, type);
    }

    public static <T> T createLiveReloadingProxyWithFailover(
            Supplier<ServerListConfig> serverListConfigSupplier,
            Class<T> type,
            AuxiliaryRemotingParameters clientParameters) {
        Supplier<T> clientFactory = () ->
                ConjureJavaRuntimeTargetFactory.DEFAULT
                        .createLiveReloadingProxyWithFailover(serverListConfigSupplier, type, clientParameters)
                        .instance();
        return SelfRefreshingProxy.create(clientFactory, type);
    }

    @VisibleForTesting
    static <T> T createProxyWithQuickFailoverForTesting(
            ServerListConfig serverListConfig,
            Class<T> type,
            AuxiliaryRemotingParameters parameters) {
        Supplier<T> clientFactory = () ->
                ConjureJavaRuntimeTargetFactory.DEFAULT
                        .createProxyWithQuickFailoverForTesting(serverListConfig, type, parameters)
                        .instance();
        return SelfRefreshingProxy.create(clientFactory, type);
    }
}
