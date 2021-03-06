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

package com.radixdlt.checkpoint;

import com.google.common.collect.ImmutableList;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.radixdlt.atommodel.tokens.MutableSupplyTokenDefinitionParticle;
import com.radixdlt.atommodel.tokens.TokenDefinitionUtils;
import com.radixdlt.consensus.Command;
import com.radixdlt.consensus.GenesisValidatorSetProvider;
import com.radixdlt.consensus.Sha256Hasher;
import com.radixdlt.consensus.VerifiedLedgerHeaderAndProof;
import com.radixdlt.constraintmachine.Spin;
import com.radixdlt.crypto.Hasher;
import com.radixdlt.fees.NativeToken;
import com.radixdlt.identifiers.RRI;
import com.radixdlt.ledger.VerifiedCommandsAndProof;
import com.radixdlt.middleware2.ClientAtom;
import com.radixdlt.properties.RuntimeProperties;
import com.radixdlt.serialization.DeserializeException;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.Serialization;
import com.radixdlt.universe.Universe;
import com.radixdlt.utils.Bytes;
import org.radix.universe.UniverseValidator;

/**
 * Configures the module in charge of "weak-subjectivity" or checkpoints
 * which the node will always align with
 */
public class CheckpointModule extends AbstractModule {

	public CheckpointModule() {
		// Nothing to do here
	}

	@Provides
	@Singleton
	private Universe universe(RuntimeProperties properties, Serialization serialization) {
		try {
			byte[] bytes = Bytes.fromBase64String(properties.get("universe"));
			Universe u = serialization.fromDson(bytes, Universe.class);
			UniverseValidator.validate(u, Sha256Hasher.withDefaultSerialization());
			return u;
		} catch (DeserializeException e) {
			throw new IllegalStateException("Error while deserialising universe", e);
		}
	}

	@Provides
	@Singleton // Don't want to recompute on each use
	@NativeToken
	private RRI nativeToken(Universe universe) {
		final String tokenName = TokenDefinitionUtils.getNativeTokenShortCode();
		ImmutableList<RRI> rris = universe.getGenesis().stream()
				.flatMap(a -> a.particles(Spin.UP))
				.filter(p -> p instanceof MutableSupplyTokenDefinitionParticle)
				.map(p -> (MutableSupplyTokenDefinitionParticle) p)
				.map(MutableSupplyTokenDefinitionParticle::getRRI)
				.filter(rri -> rri.getName().equals(tokenName))
				.collect(ImmutableList.toImmutableList());
		if (rris.isEmpty()) {
			throw new IllegalStateException("No mutable supply token " + tokenName + " in genesis");
		}
		if (rris.size() > 1) {
			throw new IllegalStateException("More than one mutable supply token " + tokenName + " in genesis");
		}
		return rris.get(0);
	}

	@Provides
	@Named("magic")
	private int magic(Universe universe) {
		return universe.getMagic();
	}

	@Provides
	@Singleton
	private VerifiedCommandsAndProof genesisCheckpoint(
		Serialization serialization,
		Universe universe,
		GenesisValidatorSetProvider initialValidatorSetProvider,
		Hasher hasher
	) {
		final ClientAtom genesisAtom = ClientAtom.convertFromApiAtom(universe.getGenesis().get(0), hasher);
		byte[] payload = serialization.toDson(genesisAtom, Output.ALL);
		Command command = new Command(payload);
		VerifiedLedgerHeaderAndProof genesisLedgerHeader = VerifiedLedgerHeaderAndProof.genesis(
			hasher.hash(command),
			initialValidatorSetProvider.genesisValidatorSet()
		);

		return new VerifiedCommandsAndProof(
			ImmutableList.of(command),
			genesisLedgerHeader
		);
	}
}
