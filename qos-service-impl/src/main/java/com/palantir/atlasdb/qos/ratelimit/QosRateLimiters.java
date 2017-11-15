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

package com.palantir.atlasdb.qos.ratelimit;

import org.immutables.value.Value;

import com.palantir.atlasdb.qos.config.QosLimitsConfig;

@Value.Immutable
public interface QosRateLimiters {

    static QosRateLimiters create(QosLimitsConfig config) {
        QosRateLimiter readLimiter = QosRateLimiter.create();
        readLimiter.updateRate(config.readBytesPerSecond());

        QosRateLimiter writeLimiter = QosRateLimiter.create();
        writeLimiter.updateRate(config.writeBytesPerSecond());

        return ImmutableQosRateLimiters.builder()
                .read(readLimiter)
                .write(writeLimiter)
                .build();
    }

    QosRateLimiter read();

    QosRateLimiter write();

}
