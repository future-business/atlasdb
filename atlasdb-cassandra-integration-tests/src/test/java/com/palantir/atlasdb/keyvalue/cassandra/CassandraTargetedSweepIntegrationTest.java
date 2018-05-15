/*
 * Copyright 2017 Palantir Technologies, Inc. All rights reserved.
 *
 * Licensed under the BSD-3 License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://opensource.org/licenses/BSD-3-Clause
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.palantir.atlasdb.keyvalue.cassandra;

import static org.mockito.Mockito.mock;

import java.util.Optional;

import org.junit.Before;

import com.palantir.atlasdb.cassandra.CassandraKeyValueServiceConfig;
import com.palantir.atlasdb.containers.CassandraContainer;
import com.palantir.atlasdb.keyvalue.api.Cell;
import com.palantir.atlasdb.keyvalue.api.KeyValueService;
import com.palantir.atlasdb.keyvalue.api.SweepResults;
import com.palantir.atlasdb.keyvalue.api.TableReference;
import com.palantir.atlasdb.sweep.AbstractSweepTest;
import com.palantir.atlasdb.sweep.queue.KvsSweepQueue;
import com.palantir.atlasdb.sweep.queue.ShardAndStrategy;
import com.palantir.atlasdb.sweep.queue.SweepTimestampProvider;
import com.palantir.atlasdb.transaction.impl.SerializableTransactionManager;

// todo(gmaretic): fix
public class CassandraTargetedSweepIntegrationTest extends AbstractSweepTest {

    private SweepTimestampProvider timestamps = mock(SweepTimestampProvider.class);
    private KvsSweepQueue sweepQueue;

    @Before
    public void setup() {
        super.setup();

        sweepQueue.createUninitialized(() -> true, () -> 1, 0, 0);
        sweepQueue.callbackInit((SerializableTransactionManager) txManager);
    }

    @Override
    protected KeyValueService getKeyValueService() {
        CassandraKeyValueServiceConfig config = CassandraContainer.KVS_CONFIG;

        return CassandraKeyValueServiceImpl.create(config, CassandraContainer.LEADER_CONFIG);
    }

    @Override
    protected Optional<SweepResults> completeSweep(TableReference tableReference, long ts) {
        sweepQueue.sweepNextBatch(ShardAndStrategy.conservative(0));
        return Optional.empty();
    }

    @Override
    protected void put(final TableReference tableRef, Cell cell, final String val, final long ts) {
    }
}
