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

package com.radixdlt.keys;

import com.google.inject.AbstractModule;
import com.radixdlt.consensus.HashSigner;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.Self;
import com.radixdlt.crypto.ECKeyPair;

import java.util.Objects;

/**
 * In memory Hash signing and identity handling
 */
public final class InMemoryBFTKeyModule extends AbstractModule {
    private final ECKeyPair keyPair;

    public InMemoryBFTKeyModule(ECKeyPair keyPair) {
        this.keyPair = Objects.requireNonNull(keyPair);
    }

    @Override
    protected void configure() {
        bind(HashSigner.class).toInstance(keyPair::sign);
        final BFTNode self = BFTNode.create(keyPair.getPublicKey());
        bind(BFTNode.class).annotatedWith(Self.class).toInstance(self);
    }
}
