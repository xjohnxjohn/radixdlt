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

package com.radixdlt.consensus.simulation.network;

import com.google.common.collect.ImmutableList;
import com.radixdlt.consensus.ConsensusEvent;
import com.radixdlt.consensus.ConsensusEventsRx;
import com.radixdlt.consensus.bft.BFTEventReducer.BFTEventSender;
import com.radixdlt.consensus.QuorumCertificate;
import com.radixdlt.consensus.SyncEpochsRPCRx;
import com.radixdlt.consensus.epoch.EpochManager.SyncEpochsRPCSender;
import com.radixdlt.consensus.VertexMetadata;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.GetVerticesErrorResponse;
import com.radixdlt.consensus.bft.GetVerticesResponse;
import com.radixdlt.consensus.NewView;
import com.radixdlt.consensus.Proposal;
import com.radixdlt.consensus.SyncVerticesRPCRx;
import com.radixdlt.consensus.bft.VertexStore.SyncVerticesRPCSender;
import com.radixdlt.consensus.Vertex;
import com.radixdlt.consensus.bft.VertexStore.GetVerticesRequest;
import com.radixdlt.consensus.Vote;
import com.radixdlt.consensus.epoch.GetEpochRequest;
import com.radixdlt.consensus.epoch.GetEpochResponse;
import com.radixdlt.crypto.Hash;
import io.reactivex.rxjava3.core.Observable;

import io.reactivex.rxjava3.schedulers.Timed;
import io.reactivex.rxjava3.subjects.ReplaySubject;
import io.reactivex.rxjava3.subjects.Subject;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Simple simulated network implementation that just sends messages to itself with a configurable latency.
 */
public class SimulationNetwork {
	public static final int DEFAULT_LATENCY = 50;

	public static final class MessageInTransit {
		private final Object content;
		private final BFTNode sender;
		private final BFTNode receiver;
		private final long delay;
		private final long delayAfterPrevious;

		private MessageInTransit(Object content, BFTNode sender, BFTNode receiver, long delay, long delayAfterPrevious) {
			this.content = Objects.requireNonNull(content);
			this.sender = sender;
			this.receiver = receiver;
			this.delay = delay;
			this.delayAfterPrevious = delayAfterPrevious;
		}

		private static MessageInTransit newMessage(Object content, BFTNode sender, BFTNode receiver) {
			return new MessageInTransit(content, sender, receiver, 0, 0);
		}

		private MessageInTransit delayed(long delay) {
			return new MessageInTransit(content, sender, receiver, delay, delay);
		}

		private MessageInTransit delayAfterPrevious(long delayAfterPrevious) {
			return new MessageInTransit(content, sender, receiver, delay, delayAfterPrevious);
		}

		public Object getContent() {
			return this.content;
		}

		public BFTNode getSender() {
			return sender;
		}

		public BFTNode getReceiver() {
			return receiver;
		}

		@Override
		public String toString() {
			return String.format("%s %s -> %s %d %d",
				content,
				sender.getSimpleName(),
				receiver.getSimpleName(),
				delay,
				delayAfterPrevious
			);
		}
	}

	/**
	 * The latency configuration for a network
	 */
	public interface LatencyProvider {

		/**
		 * If >= 0, returns the latency in milliseconds of the next message.
		 * If < 0, signifies to drop the next message.
		 *
		 * @param msg the next message
		 * @return the latency in milliseconds if >= 0, otherwise a negative number signifies a drop
		 */
		int nextLatency(MessageInTransit msg);
	}

	private final Subject<MessageInTransit> receivedMessages;
	private final Map<BFTNode, SimulatedNetworkImpl> receivers = new ConcurrentHashMap<>();
	private final LatencyProvider latencyProvider;

	private SimulationNetwork(LatencyProvider latencyProvider) {
		this.latencyProvider = latencyProvider;
		this.receivedMessages = ReplaySubject.<MessageInTransit>create(20) // To catch startup timing issues
			.toSerialized();
	}

	public static class Builder {
		private LatencyProvider latencyProvider = msg -> DEFAULT_LATENCY;

		private Builder() {
		}

		public Builder latencyProvider(LatencyProvider latencyProvider) {
			this.latencyProvider = latencyProvider;
			return this;
		}

		public SimulationNetwork build() {
			return new SimulationNetwork(latencyProvider);
		}
	}

	public static Builder builder() {
		return new Builder();
	}

	public BFTEventSender getNetworkSender(BFTNode forNode) {
		return new BFTEventSender() {
			@Override
			public void broadcastProposal(Proposal proposal, Set<BFTNode> nodes) {
				for (BFTNode reader : nodes) {
					receivedMessages.onNext(MessageInTransit.newMessage(proposal, forNode, reader));
				}
			}

			@Override
			public void sendNewView(NewView newView, BFTNode newViewLeader) {
				receivedMessages.onNext(MessageInTransit.newMessage(newView, forNode, newViewLeader));
			}

			@Override
			public void sendVote(Vote vote, BFTNode leader) {
				receivedMessages.onNext(MessageInTransit.newMessage(vote, forNode, leader));
			}
		};
	}

	private static final class SimulatedVerticesRequest implements GetVerticesRequest {
		private final Hash vertexId;
		private final int count;
		private final BFTNode requestor;

		private SimulatedVerticesRequest(BFTNode requestor, Hash vertexId, int count) {
			this.requestor = requestor;
			this.vertexId = vertexId;
			this.count = count;
		}

		@Override
		public Hash getVertexId() {
			return vertexId;
		}

		@Override
		public int getCount() {
			return count;
		}

		@Override
		public String toString() {
			return String.format("%s{vertexId=%s count=%d}", this.getClass().getSimpleName(), this.vertexId, this.count);
		}
	}


	private class SimulatedNetworkImpl implements SimulatedNetworkReceiver, SimulationSyncSender {
		private final Observable<Object> myMessages;
		private final BFTNode thisNode;
		private HashMap<Hash, Object> opaqueMap = new HashMap<>();

		private SimulatedNetworkImpl(BFTNode node) {
			this.thisNode = node;
			// filter only relevant messages (appropriate target and if receiving is allowed)
			this.myMessages = receivedMessages
				.filter(msg -> msg.receiver.equals(node))
				.groupBy(MessageInTransit::getSender)
				.flatMap(groupedObservable ->
					groupedObservable.map(msg -> {
						if (msg.sender.equals(node)) {
							return msg;
						} else {
							return msg.delayed(latencyProvider.nextLatency(msg));
						}
					})
					.filter(msg -> msg.delay >= 0)
					.timestamp(TimeUnit.MILLISECONDS)
					.scan((msg1, msg2) -> {
						int delayCarryover = (int) Math.max(msg1.time() + msg1.value().delay - msg2.time(), 0);
						int additionalDelay = (int) (msg2.value().delay - delayCarryover);
						if (additionalDelay > 0) {
							return new Timed<>(msg2.value().delayAfterPrevious(additionalDelay), msg2.time(), msg2.unit());
						} else {
							return msg2;
						}
					})
					.concatMap(p -> Observable.just(p.value()).delay(p.value().delayAfterPrevious, TimeUnit.MILLISECONDS))
					.map(MessageInTransit::getContent)
				)
				.publish()
				.refCount();
		}

		@Override
		public void sendGetVerticesRequest(Hash id, BFTNode node, int count, Object opaque) {
			final SimulatedVerticesRequest request = new SimulatedVerticesRequest(thisNode, id, count);
			opaqueMap.put(id, opaque);
			receivedMessages.onNext(MessageInTransit.newMessage(request, thisNode, node));
		}

		@Override
		public void sendGetVerticesResponse(GetVerticesRequest originalRequest, ImmutableList<Vertex> vertices) {
			SimulatedVerticesRequest request = (SimulatedVerticesRequest) originalRequest;
			Object opaque = receivers.computeIfAbsent(request.requestor, SimulatedNetworkImpl::new).opaqueMap.get(request.vertexId);
			GetVerticesResponse vertexResponse = new GetVerticesResponse(request.vertexId, vertices, opaque);
			receivedMessages.onNext(MessageInTransit.newMessage(vertexResponse, thisNode, request.requestor));
		}

		@Override
		public void sendGetVerticesErrorResponse(GetVerticesRequest originalRequest, QuorumCertificate highestQC,
			QuorumCertificate highestCommittedQC) {

			SimulatedVerticesRequest request = (SimulatedVerticesRequest) originalRequest;
			Object opaque = receivers.computeIfAbsent(request.requestor, SimulatedNetworkImpl::new).opaqueMap.get(request.vertexId);
			GetVerticesErrorResponse vertexResponse = new GetVerticesErrorResponse(request.vertexId, highestQC, highestCommittedQC, opaque);
			receivedMessages.onNext(MessageInTransit.newMessage(vertexResponse, thisNode, request.requestor));
		}

		@Override
		public void sendGetEpochRequest(BFTNode node, long epoch) {
			GetEpochRequest getEpochRequest = new GetEpochRequest(thisNode, epoch);
			receivedMessages.onNext(MessageInTransit.newMessage(getEpochRequest, thisNode, node));
		}

		@Override
		public void sendGetEpochResponse(BFTNode node, VertexMetadata ancestor) {
			GetEpochResponse getEpochResponse = new GetEpochResponse(thisNode, ancestor);
			receivedMessages.onNext(MessageInTransit.newMessage(getEpochResponse, thisNode, node));
		}

		@Override
		public Observable<ConsensusEvent> consensusEvents() {
			return myMessages.ofType(ConsensusEvent.class);
		}

		@Override
		public Observable<GetVerticesRequest> requests() {
			return myMessages.ofType(GetVerticesRequest.class);
		}

		@Override
		public Observable<GetVerticesResponse> responses() {
			return myMessages.ofType(GetVerticesResponse.class);
		}

		@Override
		public Observable<GetVerticesErrorResponse> errorResponses() {
			return myMessages.ofType(GetVerticesErrorResponse.class);
		}

		@Override
		public Observable<GetEpochRequest> epochRequests() {
			return myMessages.ofType(GetEpochRequest.class);
		}

		@Override
		public Observable<GetEpochResponse> epochResponses() {
			return myMessages.ofType(GetEpochResponse.class);
		}
	}

	public SimulatedNetworkReceiver getNetworkRx(BFTNode forNode) {
		return receivers.computeIfAbsent(forNode, SimulatedNetworkImpl::new);
	}

	public SimulationSyncSender getSyncSender(BFTNode forNode) {
		return receivers.computeIfAbsent(forNode, SimulatedNetworkImpl::new);
	}

	public interface SimulationSyncSender extends SyncVerticesRPCSender, SyncEpochsRPCSender {
	}

	public interface SimulatedNetworkReceiver extends ConsensusEventsRx, SyncVerticesRPCRx, SyncEpochsRPCRx {
	}
}
