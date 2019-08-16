package com.radixdlt.atommodel.message;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableSet;
import com.radixdlt.atomos.RadixAddress;
import com.radixdlt.atoms.Particle;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.SerializerId2;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

/**
 * A simple particle for messages which get stored in one to two addresses.
 */
@SerializerId2("radix.particles.message")
public final class MessageParticle extends Particle {
	@JsonProperty("from")
	@DsonOutput(DsonOutput.Output.ALL)
	private RadixAddress from;

	@JsonProperty("to")
	@DsonOutput(DsonOutput.Output.ALL)
	private RadixAddress to;

	/**
	 * Metadata, aka data about the data (e.g. contentType).
	 * Will consider down the line whether this is worth putting
	 * into a more concrete class (e.g. MetaData.java).
	 */
	@JsonProperty("metaData")
	@DsonOutput(DsonOutput.Output.ALL)
	private Map<String, String> metaData = new TreeMap<>();

	/**
	 * Arbitrary data
	 */
	@JsonProperty("bytes")
	@DsonOutput(DsonOutput.Output.ALL)
	private byte[] bytes;

	@JsonProperty("nonce")
	@DsonOutput(DsonOutput.Output.ALL)
	private long nonce;

	private MessageParticle() {
		super(ImmutableSet.of());
	}

	public MessageParticle(RadixAddress from, RadixAddress to, byte[] bytes) {
		super(ImmutableSet.of(from.getUID(), to.getUID()));

		this.from = Objects.requireNonNull(from, "from is required");
		this.to = Objects.requireNonNull(to, "to is required");
		this.bytes = Arrays.copyOf(bytes, bytes.length);
		this.nonce = System.nanoTime();
	}

	public MessageParticle(RadixAddress from, RadixAddress to, byte[] bytes, String contentType) {
		super(ImmutableSet.of(from.getUID(), to.getUID()));

		this.from = Objects.requireNonNull(from, "from is required");
		this.to = Objects.requireNonNull(to, "to is required");
		this.bytes = Arrays.copyOf(bytes, bytes.length);
		this.metaData.put("contentType", contentType);
		this.nonce = System.nanoTime();
	}

	Set<RadixAddress> getAddresses() {
		return ImmutableSet.of(from, to);
	}

	public RadixAddress getFrom() {
		return from;
	}

	public RadixAddress getTo() {
		return to;
	}

	public byte[] getBytes() {
		return bytes;
	}

	public Map<String, String> getMetaData() {
		return metaData;
	}

	@Override
	public String toString() {
		return String.format("%s[(%s:%s)]",
			getClass().getSimpleName(), String.valueOf(from), String.valueOf(to));
	}
}
