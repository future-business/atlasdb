/*
 * (c) Copyright 2019 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.atlasdb.transaction.encoding;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.IntStream;

import org.junit.Test;

import com.palantir.atlasdb.keyvalue.api.Cell;

public class TicketsEncodingStrategyTest {
    private final TicketsEncodingStrategy strategy = new TicketsEncodingStrategy();

    @Test
    public void cellEncodeAndDecodeAreInverses() {
        fuzzOneThousandTrials(() -> {
            long timestamp = ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE);
            Cell encoded = strategy.encodeStartTimestampAsCell(timestamp);
            assertThat(strategy.decodeCellAsStartTimestamp(encoded)).isEqualTo(timestamp);
        });
    }

    @Test
    public void commitTimestampEncodeAndDecodeAreInverses() {
        fuzzOneThousandTrials(() -> {
            long startTimestamp = ThreadLocalRandom.current().nextLong(0, Long.MAX_VALUE - 1);
            long commitTimestamp = ThreadLocalRandom.current().nextLong(startTimestamp, Long.MAX_VALUE);
            byte[] encoded = strategy.encodeCommitTimestampAsValue(startTimestamp, commitTimestamp);
            assertThat(strategy.decodeValueAsCommitTimestamp(startTimestamp, encoded)).isEqualTo(commitTimestamp);
        });
    }

    private static void fuzzOneThousandTrials(Runnable test) {
        IntStream.range(0, 1000)
                .forEach(unused -> test.run());
    }
}