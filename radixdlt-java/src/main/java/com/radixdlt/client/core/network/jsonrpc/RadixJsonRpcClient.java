package com.radixdlt.client.core.network.jsonrpc;

import com.radixdlt.client.core.atoms.AtomStatus;
import com.radixdlt.client.core.atoms.AtomStatusNotification;
import com.radixdlt.client.core.ledger.AtomEvent;
import java.util.List;
import java.util.UUID;

import org.json.JSONObject;
import org.radix.common.ID.AID;
import org.radix.common.ID.EUID;
import org.radix.serialization2.DsonOutput.Output;
import org.radix.serialization2.JsonJavaType;
import org.radix.serialization2.Serialization;
import org.radix.serialization2.client.GsonJson;
import org.radix.serialization2.client.Serialize;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.radixdlt.client.core.address.RadixUniverseConfig;
import com.radixdlt.client.core.atoms.Atom;
import com.radixdlt.client.core.ledger.AtomObservation;

import io.reactivex.Completable;
import io.reactivex.Maybe;
import io.reactivex.Observable;
import io.reactivex.Single;
import io.reactivex.disposables.Disposable;

/**
 * Responsible for managing the state across one web socket connection to a Radix Node.
 * This consists of mainly keeping track of JSON-RPC method calls and JSON-RPC subscription
 * calls.
 */
public class RadixJsonRpcClient {
	public static class JsonRpcResponse {
		private final boolean isSuccess;
		private final JsonElement jsonResponse;

		public JsonRpcResponse(boolean isSuccess, JsonElement jsonResponse) {
			this.isSuccess = isSuccess;
			this.jsonResponse = jsonResponse;
		}

		public boolean isSuccess() {
			return isSuccess;
		}

		public JsonElement getJsonResponse() {
			return jsonResponse;
		}

		public JsonElement getResult() {
			return jsonResponse.getAsJsonObject().get("result");
		}

		public JsonElement getError() {
			return jsonResponse.getAsJsonObject().get("error");
		}
	}

	private static final Logger LOGGER = LoggerFactory.getLogger(RadixJsonRpcClient.class);

	/**
	 * API version of Client, must match with Server
	 */
	public static final Integer API_VERSION = 1;

	/**
	 * The channel this JSON RPC client utilizes for messaging
	 */
	private final PersistentChannel channel;

	/**
	 * Hot observable of messages received through the channel
	 */
	private final Observable<JsonObject> messages;

	/**
	 * Cached API version of Node
	 */
	private final Single<Integer> serverApiVersion;

	/**
	 * Cached Universe of Node
	 */
	private final Single<RadixUniverseConfig> universeConfig;

	public RadixJsonRpcClient(PersistentChannel channel) {
		this.channel = channel;

		final JsonParser parser = new JsonParser();
		this.messages = this.channel.getMessages()
			.map(msg -> parser.parse(msg).getAsJsonObject())
			.publish()
			.refCount();

		Serialization serialization = Serialize.getInstance();
		this.serverApiVersion = jsonRpcCall("Api.getVersion")
			.map(JsonRpcResponse::getResult)
			.map(JsonElement::getAsJsonObject)
			.map(result -> result.get("version").getAsInt())
			.onErrorReturn(e -> {
				LOGGER.error(String.format("Error while requesting Api.getVersion: %s", e));
				return API_VERSION; // TODO assume api version matches for now until fixed in core
			})
			.cache();
		this.universeConfig = jsonRpcCall("Universe.getUniverse")
			.map(JsonRpcResponse::getResult)
			.map(element -> GsonJson.getInstance().stringFromGson(element))
			.map(result -> serialization.fromJson(result, RadixUniverseConfig.class))
			.onErrorReturn(e -> {
				LOGGER.error(String.format("Error while requesting Universe.getUniverse: %s", e));
				return null; // TODO until we have a better option
			})
			.cache();
	}

	/**
	 * Generic helper method for calling a JSON-RPC method. Deserializes the received json.
	 *
	 * @param method name of JSON-RPC method
	 * @return response from rpc method
	 */
	public Single<JsonRpcResponse> jsonRpcCall(String method, JsonElement params) {
		return Single.create(emitter -> {
			final String uuid = UUID.randomUUID().toString();

			final JsonObject requestObject = new JsonObject();
			requestObject.addProperty("id", uuid);
			requestObject.addProperty("method", method);
			requestObject.add("params", params);

			Disposable d = messages
				.filter(msg -> msg.has("id"))
				.filter(msg -> msg.get("id").isJsonNull() || msg.get("id").getAsString().equals(uuid))
				.firstOrError()
				.map(msg -> {
					final JsonObject jsonResponse = msg.getAsJsonObject();
					return new JsonRpcResponse(!jsonResponse.has("error"), jsonResponse);
				})
				.subscribe(emitter::onSuccess);

			boolean sendSuccess = channel.sendMessage(GsonJson.getInstance().stringFromGson(requestObject));
			if (!sendSuccess) {
				emitter.onError(new RuntimeException("Could not send message: " + method + " " + params));
				d.dispose();
			}
		});
	}

	/**
	 * Generic helper method for calling a JSON-RPC method with no parameters. Deserializes the received json.
	 *
	 * @param method name of JSON-RPC method
	 * @return response from rpc method
	 */
	public Single<JsonRpcResponse> jsonRpcCall(String method) {
		return this.jsonRpcCall(method, new JsonObject());
	}

	public Single<Integer> apiVersion() {
		return this.serverApiVersion;
	}

	/**
	 * Retrieve the universe the node is supporting. The result is cached for future calls.
	 *
	 * @return universe config which the node is supporting
	 */
	public Single<RadixUniverseConfig> universe() {
		return this.universeConfig;
	}

	/**
	 * Retrieve the node data for node we are connected to
	 *
	 * @return node data for node we are connected to
	 */
	public Single<NodeRunnerData> getInfo() {
		return this.jsonRpcCall("Network.getInfo")
			.map(JsonRpcResponse::getResult)
			.map(result -> Serialize.getInstance().fromJson(result.toString(), RadixSystem.class))
			.map(NodeRunnerData::new);
	}

	/**
	 * Retrieve list of nodes this node knows about
	 *
	 * @return list of nodes this node knows about
	 */
	public Single<List<NodeRunnerData>> getLivePeers() {
		JsonJavaType listOfNodeRunnerData = Serialize.getInstance().jsonCollectionType(List.class, NodeRunnerData.class);
		return this.jsonRpcCall("Network.getLivePeers")
				.map(JsonRpcResponse::getResult)
				.map(result -> Serialize.getInstance().fromJson(result.toString(), listOfNodeRunnerData));
	}

	/**
	 * Submits an atom to the node.
	 * @param atom the atom to submit
	 * @return a completable which completes when the atom is queued
	 */
	public Completable pushAtom(Atom atom) {
		JSONObject jsonAtomTemp = Serialize.getInstance().toJsonObject(atom, Output.API);
		JsonElement jsonAtom = GsonJson.getInstance().toGson(jsonAtomTemp);

		return this.jsonRpcCall("Atoms.submitAtom", jsonAtom).map(r -> {
			if (!r.isSuccess || r.getError() != null) {
				throw new RuntimeException();
			} else {
				return r;
			}
		}).ignoreElement();
	}

	/**
	 * Sends a request to receive streaming updates on an atom's status.
	 * @param subscriberId the subscriberId for the streaming updates
	 * @param aid the AID of the atom
	 * @return a completable which completes when subscription is registered
	 */
	public Completable sendGetAtomStatusNotifications(String subscriberId, AID aid) {
		final JsonObject params = new JsonObject();
		params.addProperty("aid", aid.toString());
		params.addProperty("subscriberId", subscriberId);

		return this.jsonRpcCall("Atoms.getAtomStatusNotifications", params).map(r -> {
			if (!r.isSuccess) {
				throw new RuntimeException();
			} else {
				return r;
			}
		}).ignoreElement();
	}

	/**
	 * Closes a streaming status subscription
	 * @param subscriberId the subscriberId for the streaming updates
	 * @return a completable which completes when subscription is closed
	 */
	public Completable closeAtomStatusNotifications(String subscriberId) {
		final JsonObject cancelParams = new JsonObject();
		cancelParams.addProperty("subscriberId", subscriberId);

		return this.jsonRpcCall("Atoms.closeAtomStatusNotifications", cancelParams).map(r -> {
			if (!r.isSuccess) {
				throw new RuntimeException();
			} else {
				return r;
			}
		}).ignoreElement();
	}

	/**
	 * Listens to atom status notifications
	 * @param subscriberId the subscription to listen for
	 * @return observable of status notifications
	 */
	public Observable<AtomStatusNotification> observeAtomStatusNotifications(String subscriberId) {
		return this.observeNotifications("Atoms.nextStatusEvent", subscriberId)
			.map(observedStatus -> {
				AtomStatus atomStatus = AtomStatus.valueOf(observedStatus.get("status").getAsString());
				JsonObject data = observedStatus.get("data").getAsJsonObject();
				return new AtomStatusNotification(atomStatus, data);
			});
	}

	/**
	 * Get the current status of an atom for this node
	 * @param aid the aid of the atom
	 * @return the status of the atom
	 */
	public Single<AtomStatus> getAtomStatus(AID aid) {
		JsonObject params = new JsonObject();
		params.addProperty("aid", aid.toString());
		return this.jsonRpcCall("Atoms.getAtomStatus", params)
			.map(JsonRpcResponse::getResult)
			.map(JsonElement::getAsJsonObject)
			.map(json -> AtomStatus.valueOf(json.get("status").getAsString()));
	}

	/**
	 * Queries for an atom by HID.
	 * If the node does not carry the atom (e.g. if it does not reside on the same shard) then
	 * this method will return an empty Maybe.
	 *
	 * @param hid the hash id of the atom being queried
	 * @return the atom if found, if not, return an empty Maybe
	 */
	public Maybe<Atom> getAtom(EUID hid) {
		JsonObject params = new JsonObject();
		params.addProperty("hid", hid.toString());

		JsonJavaType listOfAtom = Serialize.getInstance().jsonCollectionType(List.class, Atom.class);
		return this.jsonRpcCall("Ledger.getAtoms", params)
			.map(JsonRpcResponse::getResult)
			.<List<Atom>>map(result -> Serialize.getInstance().fromJson(result.toString(), listOfAtom))
			.flatMapMaybe(list -> list.isEmpty() ? Maybe.empty() : Maybe.just(list.get(0)));
	}

	public Observable<JsonObject> observeNotifications(String notificationMethod, String subscriberId) {
		return messages
			.filter(msg -> msg.has("method"))
			.filter(msg -> msg.get("method").getAsString().equals(notificationMethod))
			.map(msg -> msg.get("params").getAsJsonObject())
			.filter(p -> p.get("subscriberId").getAsString().equals(subscriberId));
	}

	public Observable<AtomObservation> observeAtoms(String subscriberId) {
		return this.observeNotifications("Atoms.subscribeUpdate", subscriberId)
			.flatMap(observedAtomsJson -> {
				LOGGER.debug("Received Atoms.subscribeUpdate: for {}: {}", subscriberId, observedAtomsJson);
				JsonArray atomEvents = observedAtomsJson.getAsJsonArray("atomEvents");
				boolean isHead = observedAtomsJson.has("isHead") && observedAtomsJson.get("isHead").getAsBoolean();

				return Observable.fromIterable(atomEvents)
					.map(jsonAtom -> Serialize.getInstance().fromJson(jsonAtom.toString(), AtomEvent.class))
					.map(AtomObservation::ofEvent)
					.concatWith(Maybe.fromCallable(() -> isHead ? AtomObservation.head() : null));
			});
	}

	public Completable cancelAtomsSubscribe(String subscriberId) {
		final JsonObject cancelParams = new JsonObject();
		cancelParams.addProperty("subscriberId", subscriberId);

		return this.jsonRpcCall("Atoms.cancel", cancelParams).map(r -> {
			if (!r.isSuccess) {
				throw new RuntimeException();
			} else {
				return r;
			}
		}).ignoreElement();
	}

	public Completable sendAtomsSubscribe(String subscriberId, AtomQuery atomQuery) {
		final JsonObject params = new JsonObject();
		params.add("query", atomQuery.toJson());
		params.addProperty("subscriberId", subscriberId);

		return this.jsonRpcCall("Atoms.subscribe", params).map(r -> {
			if (!r.isSuccess) {
				throw new RuntimeException();
			} else {
				return r;
			}
		}).ignoreElement();
	}

	/**
	 * Generic helper method for creating a subscription via JSON-RPC.
	 *
	 * @param method name of subscription method
	 * @param rawParams parameters to subscription method
	 * @param notificationMethod name of the JSON-RPC notification method
	 * @return Observable of emitted subscription json elements
	 */
	public Observable<JsonElement> jsonRpcSubscribe(String method, JsonObject rawParams, String notificationMethod) {
		final String subscriberId = UUID.randomUUID().toString();
		final JsonObject params = rawParams.deepCopy();
		params.addProperty("subscriberId", subscriberId);

		Observable<JsonObject> stream = this.observeNotifications(notificationMethod, subscriberId)
			.doOnDispose(() -> this.cancelAtomsSubscribe(subscriberId).subscribe());

		Observable<JsonRpcResponse> response = this.jsonRpcCall(method, params).toObservable();
		return Observable.combineLatest(stream, response, (s, r) -> {
			if (!r.isSuccess()) {
				throw new RuntimeException();
			}

			return s;
		});
	}

	/**
	 *  Retrieves all atoms from a node specified by a query. This includes all past
	 *  and future atoms. The Observable returned will never complete.
	 *
	 * @param atomQuery query specifying which atoms to retrieve
	 * @return observable of atoms
	 */
	public Observable<AtomObservation> getAtoms(AtomQuery atomQuery) {
		final JsonObject params = new JsonObject();
		params.add("query", atomQuery.toJson());

		return this.jsonRpcSubscribe("Atoms.subscribe", params, "Atoms.subscribeUpdate")
			.map(JsonElement::getAsJsonObject)
			.flatMap(observedAtomsJson -> {
				JsonArray atomEvents = observedAtomsJson.getAsJsonArray("atomEvents");
				boolean isHead = observedAtomsJson.has("isHead") && observedAtomsJson.get("isHead").getAsBoolean();

				return Observable.fromIterable(atomEvents)
					.map(jsonAtom -> Serialize.getInstance().fromJson(jsonAtom.toString(), AtomEvent.class))
					.map(AtomObservation::ofEvent)
					.concatWith(Maybe.fromCallable(() -> isHead ? AtomObservation.head() : null));
			});
	}

	public enum NodeAtomSubmissionState {
		RECEIVED(false),
		FAILED(true),
		STORED(true),
		COLLISION(true),
		ILLEGAL_STATE(true),
		UNSUITABLE_PEER(true),
		VALIDATION_ERROR(true),
		UNKNOWN_ERROR(true);

		private final boolean isComplete;

		NodeAtomSubmissionState(boolean isComplete) {
			this.isComplete = isComplete;
		}

		public boolean isComplete() {
			return isComplete;
		}
	}


	// TODO: Remove this class
	public static final class NodeAtomSubmissionUpdate {
		private final NodeAtomSubmissionState state;
		private final JsonElement data;
		private final long timestamp;

		public NodeAtomSubmissionUpdate(NodeAtomSubmissionState state, JsonElement data) {
			this.state = state;
			this.data = data;
			this.timestamp = System.currentTimeMillis();
		}

		public NodeAtomSubmissionState getState() {
			return state;
		}

		public JsonElement getData() {
			return data;
		}

		public long getTimestamp() {
			return timestamp;
		}

		@Override
		public String toString() {
			return timestamp + " " + state + " " + data;
		}
	}
}
