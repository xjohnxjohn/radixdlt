package com.radixdlt.consensus;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.SerializerConstants;
import com.radixdlt.serialization.SerializerDummy;
import com.radixdlt.serialization.SerializerId2;
import java.util.Objects;
import javax.annotation.concurrent.Immutable;

@Immutable
@SerializerId2("consensus.vote_data")
public final class VoteData {
	@JsonProperty(SerializerConstants.SERIALIZER_NAME)
	@DsonOutput(value = {Output.API, Output.WIRE, Output.PERSIST})
	SerializerDummy serializer = SerializerDummy.DUMMY;

	@JsonProperty("proposed")
	@DsonOutput(Output.ALL)
	private final VertexMetadata proposed;

	@JsonProperty("parent")
	@DsonOutput(Output.ALL)
	private final VertexMetadata parent;

	VoteData() {
		// Serializer only
		this.proposed = null;
		this.parent = null;
	}

	public VoteData(VertexMetadata proposed, VertexMetadata parent) {
		this.proposed = Objects.requireNonNull(proposed);
		this.parent = parent;
	}

	public VertexMetadata getProposed() {
		return proposed;
	}

	public VertexMetadata getParent() {
		return parent;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		VoteData that = (VoteData) o;
		return Objects.equals(proposed, that.proposed)
			&& Objects.equals(parent, that.parent);
	}

	@Override
	public int hashCode() {
		return Objects.hash(proposed, parent);
	}
}
