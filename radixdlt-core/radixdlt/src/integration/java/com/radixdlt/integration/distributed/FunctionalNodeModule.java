package com.radixdlt.integration.distributed;

import com.google.inject.AbstractModule;
import com.google.inject.Module;
import com.radixdlt.ConsensusModule;
import com.radixdlt.DispatcherModule;
import com.radixdlt.EpochsConsensusModule;
import com.radixdlt.EpochsLedgerUpdateModule;
import com.radixdlt.EpochsSyncModule;
import com.radixdlt.LedgerCommandGeneratorModule;
import com.radixdlt.LedgerLocalMempoolModule;
import com.radixdlt.LedgerModule;
import com.radixdlt.NoFeeModule;
import com.radixdlt.SyncServiceModule;
import com.radixdlt.mempool.EmptyMempool;
import com.radixdlt.mempool.Mempool;
import com.radixdlt.mempool.MempoolReceiverModule;
import com.radixdlt.statecomputer.RadixEngineModule;

public class FunctionalNodeModule extends AbstractModule {
	private final boolean hasSharedMempool;
	private final boolean hasConsensus;
	private final boolean hasSync;

	// State manager
	private final boolean hasLedger;
	private final boolean hasMempool;
	private final boolean hasRadixEngine;

	private final boolean hasEpochs;

	// FIXME: This is required for now for shared syncing, remove after refactor
	private final Module mockedSyncServiceModule = new MockedSyncServiceModule();

	public FunctionalNodeModule(
		boolean hasSharedMempool,
		boolean hasConsensus,
		boolean hasLedger,
		boolean hasMempool,
		boolean hasRadixEngine,
		boolean hasEpochs,
		boolean hasSync
	) {
		this.hasSharedMempool = hasSharedMempool;
		this.hasConsensus = hasConsensus;
		this.hasLedger = hasLedger;
		this.hasMempool = hasMempool;
		this.hasRadixEngine = hasRadixEngine;
		this.hasEpochs = hasEpochs;
		this.hasSync = hasSync;
	}

	@Override
	public void configure() {
		install(new DispatcherModule());

		// Shared Mempool
		if (hasSharedMempool) {
			install(new MempoolReceiverModule());
		}

		// Consensus
		if (hasConsensus) {
			install(new ConsensusModule());
			if (hasEpochs) {
				install(new EpochsConsensusModule());
			}
		}

		// Sync
		if (hasLedger) {
			if (!hasSync) {
				install(mockedSyncServiceModule);
			} else {
				install(new SyncServiceModule());
				install(new MockedCommittedReaderModule());
				if (hasEpochs) {
					install(new EpochsSyncModule());
				}
			}
		}

		// State Manager
		if (!hasLedger) {
			install(new MockedLedgerModule());
		} else {
			install(new LedgerModule());

			if (!hasMempool) {
				install(new MockedCommandGeneratorModule());

				// TODO: Remove once mempool fixed
				install(new AbstractModule() {
					@Override
					public void configure() {
						bind(Mempool.class).to(EmptyMempool.class);
					}
				});

				if (!hasEpochs) {
					install(new MockedStateComputerModule());
				} else {
					install(new MockedStateComputerWithEpochsModule());
				}
			} else {
				install(new LedgerCommandGeneratorModule());
				install(new LedgerLocalMempoolModule(10));

				if (!hasRadixEngine) {
					install(new MockedMempoolStateComputerModule());
				} else {
					install(new NoFeeModule());
					install(new RadixEngineModule());
					install(new MockedRadixEngineStoreModule());
					install(new MockedValidatorComputersModule());
				}
			}

			if (hasEpochs) {
				install(new EpochsLedgerUpdateModule());
			}
		}
	}
}
