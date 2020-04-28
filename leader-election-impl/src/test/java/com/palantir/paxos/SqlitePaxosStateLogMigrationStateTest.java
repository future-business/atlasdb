/*
 * (c) Copyright 2020 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.paxos;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.util.function.Supplier;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class SqlitePaxosStateLogMigrationStateTest {
    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private static final String LOG_NAMESPACE_1 = "test_namespace";

    private Supplier<Connection> connSupplier;
    private SqlitePaxosStateLogMigrationState migrationState;

    @Before
    public void setup() {
        connSupplier = SqliteConnections
                .createSqliteDatabase(tempFolder.getRoot().toPath().resolve("test.db").toString());
        migrationState = SqlitePaxosStateLogMigrationState.create(LOG_NAMESPACE_1, connSupplier);
    }

    @Test
    public void initialStateIsNotMigrated() {
        assertThat(migrationState.hasAlreadyMigrated()).isFalse();
    }

    @Test
    public void canSetStateToMigrated() {
        migrationState.finishMigration();
        assertThat(migrationState.hasAlreadyMigrated()).isTrue();
    }

    @Test
    public void canSetStateToMigratedMultipleTimes() {
        migrationState.finishMigration();
        migrationState.finishMigration();
        migrationState.finishMigration();
        assertThat(migrationState.hasAlreadyMigrated()).isTrue();
    }

    @Test
    public void finishingMigrationForOneNamespaceDoesNotSetFlagForOthers() {
        migrationState.finishMigration();

        SqlitePaxosStateLogMigrationState otherState = SqlitePaxosStateLogMigrationState
                .create("other", connSupplier);
        assertThat(otherState.hasAlreadyMigrated()).isFalse();
        otherState.finishMigration();
        assertThat(otherState.hasAlreadyMigrated()).isTrue();
    }
}
