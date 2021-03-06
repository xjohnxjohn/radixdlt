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

package com.radixdlt.statecomputer;

import java.util.Objects;

/**
 * An event which signifies that a command has been proposed but which
 * does not pass verification.
 */
public final class InvalidProposedCommand {
    private final Exception e;

    private InvalidProposedCommand(Exception e) {
        this.e = e;
    }

    public static InvalidProposedCommand create(Exception e) {
        return new InvalidProposedCommand(e);
    }

    @Override
    public String toString() {
        return String.format("%s{ex=%s}", this.getClass().getSimpleName(), this.e.toString());
    }

    @Override
    public int hashCode() {
        return Objects.hash(e);
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof InvalidProposedCommand)) {
            return false;
        }

        InvalidProposedCommand other = (InvalidProposedCommand) o;
        return Objects.equals(this.e, other.e);
    }
}
