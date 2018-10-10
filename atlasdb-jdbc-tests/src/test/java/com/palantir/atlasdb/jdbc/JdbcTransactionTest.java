/*
 * Copyright 2015 Palantir Technologies, Inc. All rights reserved.
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
package com.palantir.atlasdb.jdbc;

import java.util.Optional;

import org.junit.ClassRule;

import com.palantir.atlasdb.keyvalue.api.KeyValueService;
import com.palantir.atlasdb.keyvalue.impl.CloseableResourceManager;
import com.palantir.atlasdb.transaction.api.TransactionManager;
import com.palantir.atlasdb.transaction.impl.AbstractTransactionTest;

public class JdbcTransactionTest extends AbstractTransactionTest {
    @ClassRule
    public static final CloseableResourceManager KVS = new CloseableResourceManager(JdbcTests::createEmptyKvs);

    @Override
    protected KeyValueService getKeyValueService() {
        return KVS.getKvs();
    }

    @Override
    protected void registerTransactionManager(TransactionManager transactionManager) {
        KVS.registerTm(transactionManager);
    }

    @Override
    protected Optional<TransactionManager> getRegisteredTransactionManager() {
        return KVS.getLastRegisteredTm();
    }

    @Override
    protected boolean supportsReverse() {
        return true;
    }
}
