/*
 * (C) Copyright 2021 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 */

package com.radixdlt.integration.distributed.simulation;

import com.google.inject.AbstractModule;
import com.google.inject.Module;
import com.google.inject.multibindings.ProvidesIntoMap;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.integration.distributed.simulation.application.TimestampChecker;
import com.radixdlt.integration.distributed.simulation.invariants.consensus.AllProposalsHaveDirectParentsInvariant;
import com.radixdlt.integration.distributed.simulation.invariants.consensus.LivenessInvariant;
import com.radixdlt.integration.distributed.simulation.invariants.consensus.NoTimeoutsInvariant;
import com.radixdlt.integration.distributed.simulation.invariants.consensus.NodeEvents;
import com.radixdlt.integration.distributed.simulation.invariants.consensus.NoneCommittedInvariant;
import com.radixdlt.integration.distributed.simulation.invariants.consensus.SafetyInvariant;
import com.radixdlt.integration.distributed.simulation.invariants.consensus.VertexRequestRateInvariant;
import com.radixdlt.integration.distributed.simulation.invariants.epochs.EpochViewInvariant;
import com.radixdlt.integration.distributed.simulation.network.SimulationNetwork;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Monitors which checks things in the consensus module
 */
public final class ConsensusMonitors {

    public static Module timestampChecker() {
        return timestampChecker(Duration.ofSeconds(1));
    }

    public static Module timestampChecker(Duration maxDelay) {
        return new AbstractModule() {
            @ProvidesIntoMap
            @MonitorKey(Monitor.TIMESTAMP_CHECK)
            public TestInvariant timestampsInvariant() {
                return new TimestampChecker(maxDelay);
            }
        };
    }

    public static Module vertexRequestRate(int permitsPerSecond) {
        return new AbstractModule() {
            @ProvidesIntoMap
            @MonitorKey(Monitor.VERTEX_REQUEST_RATE)
            TestInvariant vertexRequestRateInvariant(NodeEvents nodeEvents) {
                return new VertexRequestRateInvariant(nodeEvents, permitsPerSecond);
            }
        };
    }

    public static Module liveness() {
        return liveness(8 * SimulationNetwork.DEFAULT_LATENCY, TimeUnit.MILLISECONDS);
    }

    public static Module liveness(long duration, TimeUnit timeUnit) {
        return new AbstractModule() {
            @ProvidesIntoMap
            @MonitorKey(Monitor.LIVENESS)
            TestInvariant livenessInvariant(NodeEvents nodeEvents) {
                return new LivenessInvariant(nodeEvents, duration, timeUnit);
            }
        };
    }

    public static Module safety() {
        return new AbstractModule() {
            @ProvidesIntoMap
            @MonitorKey(Monitor.SAFETY)
            TestInvariant safetyInvariant(NodeEvents nodeEvents) {
                return new SafetyInvariant(nodeEvents);
            }
        };
    }

    public static Module noTimeouts() {
        return new AbstractModule() {
            @ProvidesIntoMap
            @MonitorKey(Monitor.NO_TIMEOUTS)
            TestInvariant noTimeoutsInvariant(NodeEvents nodeEvents) {
                return new NoTimeoutsInvariant(nodeEvents);
            }
        };
    }

    public static Module directParents() {
        return new AbstractModule() {
            @ProvidesIntoMap
            @MonitorKey(Monitor.DIRECT_PARENTS)
            TestInvariant directParentsInvariant() {
                return new AllProposalsHaveDirectParentsInvariant();
            }
        };
    }

    public static Module noneCommitted() {
        return new AbstractModule() {
            @ProvidesIntoMap
            @MonitorKey(Monitor.NONE_COMMITTED)
            TestInvariant noneCommittedInvariant(NodeEvents nodeEvents) {
                return new NoneCommittedInvariant(nodeEvents);
            }
        };
    }

    public static Module epochCeilingView(View epochCeilingView) {
        return new AbstractModule() {
            @ProvidesIntoMap
            @MonitorKey(Monitor.EPOCH_CEILING_VIEW)
            TestInvariant epochHighViewInvariant(NodeEvents nodeEvents) {
                return new EpochViewInvariant(epochCeilingView, nodeEvents);
            }
        };
    }

    private ConsensusMonitors() {
        throw new IllegalStateException("Cannot instantiate.");
    }
}
