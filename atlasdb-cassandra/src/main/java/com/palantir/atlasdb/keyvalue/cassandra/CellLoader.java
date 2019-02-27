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
package com.palantir.atlasdb.keyvalue.cassandra;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;

import org.apache.cassandra.thrift.ColumnOrSuperColumn;
import org.apache.cassandra.thrift.ColumnParent;
import org.apache.cassandra.thrift.ConsistencyLevel;
import org.apache.cassandra.thrift.SlicePredicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import com.google.common.collect.Ordering;
import com.google.common.collect.TreeMultimap;
import com.google.common.primitives.UnsignedBytes;
import com.palantir.atlasdb.AtlasDbConstants;
import com.palantir.atlasdb.keyvalue.api.Cell;
import com.palantir.atlasdb.keyvalue.api.TableReference;
import com.palantir.atlasdb.keyvalue.cassandra.thrift.SlicePredicates;
import com.palantir.atlasdb.logging.LoggingArgs;
import com.palantir.atlasdb.util.AnnotatedCallable;
import com.palantir.atlasdb.util.AnnotationType;
import com.palantir.common.base.FunctionCheckedException;
import com.palantir.logsafe.SafeArg;

class CellLoader {
    private static final Logger log = LoggerFactory.getLogger(CellLoader.class);

    private final CassandraClientPool clientPool;
    private final WrappingQueryRunner queryRunner;
    private final TaskRunner taskRunner;

    CellLoader(CassandraClientPool clientPool, WrappingQueryRunner queryRunner, TaskRunner taskRunner) {
        this.clientPool = clientPool;
        this.queryRunner = queryRunner;
        this.taskRunner = taskRunner;
    }

    Multimap<Cell, Long> getAllTimestamps(TableReference tableRef, Set<Cell> cells, long ts,
            ConsistencyLevel consistency) {
        CassandraKeyValueServices.AllTimestampsCollector collector =
                new CassandraKeyValueServices.AllTimestampsCollector();
        loadWithTs("getAllTimestamps", tableRef, cells, ts, true, collector, consistency);
        return collector.getCollectedResults();
    }

    void loadWithTs(String kvsMethodName,
            TableReference tableRef,
            Set<Cell> cells,
            long startTs,
            boolean loadAllTs,
            CassandraKeyValueServices.ThreadSafeResultVisitor visitor,
            ConsistencyLevel consistency) {
        Map<InetSocketAddress, List<Cell>> hostsAndCells = HostPartitioner.partitionByHost(clientPool, cells,
                Cell::getRowName);
        int totalPartitions = hostsAndCells.keySet().size();

        if (log.isTraceEnabled()) {
            log.trace(
                    "Loading {} cells from {} {}starting at timestamp {}, partitioned across {} nodes.",
                    SafeArg.of("cells", cells.size()),
                    LoggingArgs.tableRef(tableRef),
                    SafeArg.of("timestampClause", loadAllTs ? "for all timestamps " : ""),
                    SafeArg.of("startTs", startTs),
                    SafeArg.of("totalPartitions", totalPartitions));
        }

        List<Callable<Void>> tasks = Lists.newArrayList();
        for (Map.Entry<InetSocketAddress, List<Cell>> hostAndCells : hostsAndCells.entrySet()) {
            if (log.isTraceEnabled()) {
                log.trace(
                        "Requesting {} cells from {} {}starting at timestamp {} on {}",
                        SafeArg.of("cells", hostsAndCells.values().size()),
                        LoggingArgs.tableRef(tableRef),
                        SafeArg.of("timestampClause", loadAllTs ? "for all timestamps " : ""),
                        SafeArg.of("startTs", startTs),
                        SafeArg.of("ipPort", hostAndCells.getKey()));
            }

            tasks.addAll(getLoadWithTsTasksForSingleHost(kvsMethodName,
                    hostAndCells.getKey(),
                    tableRef,
                    hostAndCells.getValue(),
                    startTs,
                    loadAllTs,
                    visitor,
                    consistency));
        }

        taskRunner.runAllTasksCancelOnFailure(tasks);
    }

    // TODO(unknown): after cassandra api change: handle different column select per row
    private List<Callable<Void>> getLoadWithTsTasksForSingleHost(final String kvsMethodName,
            final InetSocketAddress host,
            final TableReference tableRef,
            final List<Cell> cells,
            final long startTs,
            final boolean loadAllTs,
            final CassandraKeyValueServices.ThreadSafeResultVisitor visitor,
            final ConsistencyLevel consistency) {
        final ColumnParent colFam = new ColumnParent(CassandraKeyValueServiceImpl.internalTableName(tableRef));

        List<Callable<Void>> tasks = Lists.newArrayList();

        // TODO (jkong): This is probably not the correct constant!
        for (final List<Cell> partition : Lists.partition(
                cells, AtlasDbConstants.TRANSACTION_TIMESTAMP_LOAD_BATCH_LIMIT)) {
            Callable<Void> multiGetCallable = () -> clientPool.runWithRetryOnHost(
                    host,
                    new FunctionCheckedException<CassandraClient, Void, Exception>() {
                        @Override
                        public Void apply(CassandraClient client) throws Exception {
                            List<KeyPredicate> query = Lists.newArrayListWithExpectedSize(cells.size());
                            for (Cell cell : partition) {
                                // TODO (jkong): Seems a bit wasteful to keep making predicates
                                SlicePredicates.Range range = SlicePredicates.Range.singleColumn(cell.getColumnName(),
                                        startTs);
                                SlicePredicates.Limit limit =
                                        loadAllTs ? SlicePredicates.Limit.NO_LIMIT : SlicePredicates.Limit.ONE;
                                SlicePredicate predicate = SlicePredicates.create(range, limit);

                                query.add(new KeyPredicate().setPredicate(predicate));
                            }

                            if (log.isTraceEnabled()) {
                                log.trace("Requesting {} cells from {} {}starting at timestamp {} on {}",
                                        SafeArg.of("cells", partition.size()),
                                        LoggingArgs.tableRef(tableRef),
                                        SafeArg.of("timestampClause", loadAllTs ? "for all timestamps " : ""),
                                        SafeArg.of("startTs", startTs),
                                        SafeArg.of("host", CassandraLogHelper.host(host)));
                            }

                            Map<KeyPredicate, List<ColumnOrSuperColumn>> results = queryRunner.multiget_multislice(
                                    kvsMethodName, client, tableRef, query, consistency);
                            Map<ByteBuffer, List<ColumnOrSuperColumn>> aggregatedResults = Maps.newHashMap();
                            results.forEach((keyPredicate, columns) -> {
                                aggregatedResults.merge(keyPredicate.key, columns, (existingColumns, newColumns) -> {
                                    existingColumns.addAll(newColumns);
                                    return existingColumns;
                                });
                            });
                            visitor.visit(aggregatedResults);
                            return null;
                        }

                        @Override
                        public String toString() {
                            return "multiget_multislice(" + host + ", " + colFam + ", "
                                    + partition.size() + " cells" + ")";
                        }
                    });
            tasks.add(AnnotatedCallable.wrapWithThreadName(AnnotationType.PREPEND,
                    "Atlas loadWithTs " + partition.size() + " cells from " + tableRef + " on " + host,
                    multiGetCallable));
        }
        return tasks;
    }
}
