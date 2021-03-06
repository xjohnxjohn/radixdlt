/*
 * (C) Copyright 2020 Radix DLT Ltd
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

package com.radixdlt.environment.rx;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import com.radixdlt.chaos.mempoolfiller.MempoolFillerUpdate;
import com.radixdlt.chaos.mempoolfiller.ScheduledMempoolFill;
import com.radixdlt.chaos.messageflooder.MessageFlooderUpdate;
import com.radixdlt.chaos.messageflooder.ScheduledMessageFlood;
import com.radixdlt.consensus.bft.BFTHighQCUpdate;
import com.radixdlt.consensus.bft.BFTRebuildUpdate;
import com.radixdlt.consensus.epoch.Epoched;
import com.radixdlt.consensus.liveness.EpochLocalTimeoutOccurrence;
import com.radixdlt.consensus.bft.BFTCommittedUpdate;
import com.radixdlt.consensus.bft.BFTInsertUpdate;
import com.radixdlt.consensus.bft.ViewUpdate;
import com.radixdlt.consensus.epoch.EpochViewUpdate;
import com.radixdlt.consensus.liveness.LocalTimeoutOccurrence;
import com.radixdlt.consensus.liveness.ScheduledLocalTimeout;
import com.radixdlt.consensus.sync.VertexRequestTimeout;
import com.radixdlt.environment.Environment;
import com.radixdlt.environment.LocalEvents;
import com.radixdlt.epochs.EpochsLedgerUpdate;
import com.radixdlt.ledger.LedgerUpdate;
import com.radixdlt.mempool.MempoolAddFailure;
import com.radixdlt.statecomputer.AtomCommittedToLedger;
import com.radixdlt.sync.LocalSyncRequest;
import com.radixdlt.sync.LocalSyncServiceAccumulatorProcessor.SyncInProgress;
import com.radixdlt.utils.ThreadFactories;
import io.reactivex.rxjava3.core.Observable;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Environment utilizing RxJava
 */
public class RxEnvironmentModule extends AbstractModule {

	@Override
	public void configure() {
		ScheduledExecutorService ses = Executors.newSingleThreadScheduledExecutor(ThreadFactories.daemonThreads("TimeoutSender"));
		bind(Environment.class).to(RxEnvironment.class);
		bind(ScheduledExecutorService.class).toInstance(ses);

		bind(new TypeLiteral<Observable<MempoolAddFailure>>() { }).toProvider(new ObservableProvider<>(MempoolAddFailure.class));
		bind(new TypeLiteral<Observable<ScheduledLocalTimeout>>() { }).toProvider(new ObservableProvider<>(ScheduledLocalTimeout.class));
		bind(new TypeLiteral<Observable<SyncInProgress>>() { }).toProvider(new ObservableProvider<>(SyncInProgress.class));
		bind(new TypeLiteral<Observable<BFTInsertUpdate>>() { }).toProvider(new ObservableProvider<>(BFTInsertUpdate.class));
		bind(new TypeLiteral<Observable<BFTRebuildUpdate>>() { }).toProvider(new ObservableProvider<>(BFTRebuildUpdate.class));
		bind(new TypeLiteral<Observable<BFTHighQCUpdate>>() { }).toProvider(new ObservableProvider<>(BFTHighQCUpdate.class));
		bind(new TypeLiteral<Observable<BFTCommittedUpdate>>() { }).toProvider(new ObservableProvider<>(BFTCommittedUpdate.class));
		bind(new TypeLiteral<Observable<VertexRequestTimeout>>() { }).toProvider(new ObservableProvider<>(VertexRequestTimeout.class));
		bind(new TypeLiteral<Observable<LocalSyncRequest>>() { }).toProvider(new ObservableProvider<>(LocalSyncRequest.class));
		bind(new TypeLiteral<Observable<LocalTimeoutOccurrence>>() { }).toProvider(new ObservableProvider<>(LocalTimeoutOccurrence.class));
		bind(new TypeLiteral<Observable<EpochLocalTimeoutOccurrence>>() { })
			.toProvider(new ObservableProvider<>(EpochLocalTimeoutOccurrence.class));
		bind(new TypeLiteral<Observable<EpochViewUpdate>>() { }).toProvider(new ObservableProvider<>(EpochViewUpdate.class));
		bind(new TypeLiteral<Observable<ViewUpdate>>() { }).toProvider(new ObservableProvider<>(ViewUpdate.class));
		bind(new TypeLiteral<Observable<AtomCommittedToLedger>>() { }).toProvider(new ObservableProvider<>(AtomCommittedToLedger.class));
		bind(new TypeLiteral<Observable<MessageFlooderUpdate>>() { }).toProvider(new ObservableProvider<>(MessageFlooderUpdate.class));
		bind(new TypeLiteral<Observable<ScheduledMessageFlood>>() { }).toProvider(new ObservableProvider<>(ScheduledMessageFlood.class));
		bind(new TypeLiteral<Observable<MempoolFillerUpdate>>() { }).toProvider(new ObservableProvider<>(MempoolFillerUpdate.class));
		bind(new TypeLiteral<Observable<ScheduledMempoolFill>>() { }).toProvider(new ObservableProvider<>(ScheduledMempoolFill.class));
		bind(new TypeLiteral<Observable<Epoched<ScheduledLocalTimeout>>>() { })
			.toProvider(new ObservableProvider<>(new TypeLiteral<Epoched<ScheduledLocalTimeout>>() { }));
		bind(new TypeLiteral<Observable<LedgerUpdate>>() { }).toProvider(new ObservableProvider<>(LedgerUpdate.class));
		bind(new TypeLiteral<Observable<EpochsLedgerUpdate>>() { }).toProvider(new ObservableProvider<>(EpochsLedgerUpdate.class));
	}

	@Provides
	@Singleton
	private RxEnvironment rxEnvironment(
		ScheduledExecutorService ses,
		Set<RxRemoteDispatcher<?>> dispatchers,
		@LocalEvents Set<Class<?>> localProcessedEventClasses
	) {
		return new RxEnvironment(
			Set.of(new TypeLiteral<Epoched<ScheduledLocalTimeout>>() { }),
			localProcessedEventClasses,
			ses,
			dispatchers
		);
	}
}
