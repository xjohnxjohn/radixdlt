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

package com.radixdlt.chaos.mempoolfiller;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.radixdlt.atommodel.Atom;
import com.radixdlt.atommodel.tokens.MutableSupplyTokenDefinitionParticle;
import com.radixdlt.atommodel.tokens.TokDefParticleFactory;
import com.radixdlt.atommodel.tokens.TokenDefinitionUtils;
import com.radixdlt.atommodel.tokens.TokenPermission;
import com.radixdlt.atommodel.tokens.TransferrableTokensParticle;
import com.radixdlt.constraintmachine.Spin;
import com.radixdlt.identifiers.RRI;
import com.radixdlt.identifiers.RadixAddress;
import com.radixdlt.middleware.ParticleGroup;
import com.radixdlt.utils.UInt256;

import java.util.LinkedList;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Wallet where all state is kept in memory
 */
public final class InMemoryWallet {
	private final RRI tokenRRI;
	private final RadixAddress address;
	private final ImmutableList<TransferrableTokensParticle> particles;
	private final UInt256 balance;
	private final TokDefParticleFactory factory;
	private final UInt256 fee = UInt256.TEN.pow(TokenDefinitionUtils.SUB_UNITS_POW_10 - 3).multiply(UInt256.from(50));

	private InMemoryWallet(RRI tokenRRI, RadixAddress address, ImmutableList<TransferrableTokensParticle> particles) {
		this.tokenRRI = tokenRRI;
		this.address = address;
		this.particles = particles;
		this.balance = particles.stream()
			.map(TransferrableTokensParticle::getAmount)
			.reduce(UInt256::add)
			.orElse(UInt256.ZERO);
		this.factory = TokDefParticleFactory.create(
			tokenRRI,
			ImmutableMap.of(
				MutableSupplyTokenDefinitionParticle.TokenTransition.BURN, TokenPermission.ALL,
				MutableSupplyTokenDefinitionParticle.TokenTransition.MINT, TokenPermission.TOKEN_OWNER_ONLY
			),
			UInt256.ONE
		);
	}

	public static InMemoryWallet create(RRI tokenRRI, RadixAddress address) {
		Objects.requireNonNull(tokenRRI);
		Objects.requireNonNull(address);

		return new InMemoryWallet(tokenRRI, address, ImmutableList.of());
	}

	public UInt256 getBalance() {
		return balance;
	}

	private Optional<UInt256> downParticles(
		UInt256 amount,
		LinkedList<TransferrableTokensParticle> particles,
		Consumer<TransferrableTokensParticle> onDown
	) {
		UInt256 spent = UInt256.ZERO;
		while (spent.compareTo(amount) < 0 && !particles.isEmpty()) {
			TransferrableTokensParticle particle = particles.removeFirst();
			onDown.accept(particle);
			spent = spent.add(particle.getAmount());
		}

		if (spent.compareTo(amount) < 0) {
			return Optional.empty();
		}

		return Optional.of(spent.subtract(amount));
	}

	private Optional<Atom> createTransaction(LinkedList<TransferrableTokensParticle> mutableList, RadixAddress to, UInt256 amount) {
		Atom atom = new Atom();
		ParticleGroup.ParticleGroupBuilder feeBuilder = ParticleGroup.builder();
		feeBuilder.addParticle(factory.createUnallocated(fee), Spin.UP);
		Optional<UInt256> remainder = downParticles(fee, mutableList, p -> feeBuilder.addParticle(p, Spin.DOWN));
		if (remainder.isEmpty()) {
			return Optional.empty();
		}
		remainder.filter(r -> !r.isZero()).ifPresent(r -> {
			TransferrableTokensParticle particle = factory.createTransferrable(address, r);
			mutableList.add(particle);
			feeBuilder.addParticle(particle, Spin.UP);
		});
		atom.addParticleGroup(feeBuilder.build());

		ParticleGroup.ParticleGroupBuilder builder = ParticleGroup.builder();
		builder.addParticle(factory.createTransferrable(to, amount), Spin.UP);
		Optional<UInt256> remainder2 = downParticles(amount, mutableList, p -> builder.addParticle(p, Spin.DOWN));
		if (remainder2.isEmpty()) {
			return Optional.empty();
		}
		remainder2.filter(r -> !r.isZero()).ifPresent(r -> {
			TransferrableTokensParticle particle = factory.createTransferrable(address, r);
			mutableList.add(particle);
			builder.addParticle(particle, Spin.UP);
		});
		atom.addParticleGroup(builder.build());
		return Optional.of(atom);
	}

	public Set<Atom> createParallelTransactions(RadixAddress to, int max) {
		Stream<Optional<Atom>> atoms = particles.stream()
			.filter(t -> t.getAmount().compareTo(fee.multiply(UInt256.TWO)) > 0)
			.map(t -> {
				var mutableList = new LinkedList<TransferrableTokensParticle>();
				mutableList.add(t);
				UInt256 amount = t.getAmount().subtract(fee).divide(UInt256.TWO);
				return createTransaction(mutableList, to, amount.isZero() ? UInt256.ONE : amount);
			});

	    var mutableList = new LinkedList<TransferrableTokensParticle>();
	    particles.stream().filter(t -> t.getAmount().compareTo(fee.multiply(UInt256.TWO)) <= 0)
			.limit(3)
			.forEach(mutableList::add);
		UInt256 dustAmount = particles.stream()
			.map(TransferrableTokensParticle::getAmount)
			.filter(a -> a.compareTo(fee.multiply(UInt256.TWO)) <= 0)
			.reduce(UInt256.ZERO, UInt256::add);
		Stream<Optional<Atom>> dustAtom = Stream.of(createTransaction(mutableList, to, dustAmount.subtract(fee)));

		return Stream.concat(atoms, dustAtom)
			.filter(Optional::isPresent)
			.map(Optional::get)
			.limit(max)
			.collect(Collectors.toSet());
	}

	public InMemoryWallet addParticle(TransferrableTokensParticle p) {
		if (!p.getTokDefRef().equals(tokenRRI)) {
			return this;
		}

		if (!address.equals(p.getAddress())) {
			return this;
		}

		return new InMemoryWallet(
			tokenRRI,
			address,
			ImmutableList.<TransferrableTokensParticle>builder()
				.addAll(particles)
				.add(p)
				.build()
		);
	}

	public InMemoryWallet removeParticle(TransferrableTokensParticle p) {
		if (!p.getTokDefRef().equals(tokenRRI)) {
			return this;
		}

		if (!address.equals(p.getAddress())) {
			return this;
		}

		return new InMemoryWallet(
			tokenRRI,
			address,
			particles.stream()
				.filter(particle -> !p.equals(particle))
				.collect(ImmutableList.toImmutableList())
		);
	}
}
