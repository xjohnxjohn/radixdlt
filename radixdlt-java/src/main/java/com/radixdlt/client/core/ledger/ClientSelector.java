package com.radixdlt.client.core.ledger;

import com.radixdlt.client.core.address.RadixUniverseConfig;
import com.radixdlt.client.core.network.RadixJsonRpcClient;
import com.radixdlt.client.core.network.RadixNetwork;
import com.radixdlt.client.core.network.WebSocketClient.RadixClientStatus;
import io.reactivex.Maybe;
import io.reactivex.Observable;
import io.reactivex.Single;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Given a network, selects the node to connect to
 */
public class ClientSelector {
	private static final Logger LOGGER = LoggerFactory.getLogger(ClientSelector.class);

	/**
	 * The Universe the node we return must match
	 */
	private final RadixUniverseConfig config;

	/**
	 * Flag whether to check universe or not
	 */
	private final boolean checkUniverse;

	/**
	 * The amount of time to delay in between node connection requests
	 */
	private final int delaySecs;

	/**
	 * The network of peers available to connect to
	 */
	private final RadixNetwork radixNetwork;

	public ClientSelector(RadixUniverseConfig config, RadixNetwork radixNetwork, boolean checkUniverse) {
		this.config = config;
		this.radixNetwork = radixNetwork;
		this.checkUniverse = checkUniverse;
		this.delaySecs = 3;
	}

	/**
	 * Returns a cold observable of the first peer found which supports
	 * a set short shards which intersects with a given shard
	 *
	 * @param shard a shards to find an intersection with
	 * @return a cold observable of the first matching Radix client
	 */
	public Single<RadixJsonRpcClient> getRadixClient(Long shard) {
		return getRadixClient(Collections.singleton(shard));
	}

	/**
	 * Returns a cold observable of the first peer found which supports
	 * a set short shards which intersects with a given set of shards.
	 *
	 * @param shards set of shards to find an intersection with
	 * @return a cold observable of the first matching Radix client
	 */
	public Single<RadixJsonRpcClient> getRadixClient(Set<Long> shards) {
		if (shards.isEmpty()) {
			throw new IllegalArgumentException("Shards cannot be empty to obtain a radixClient.");
		}

		return this.radixNetwork.getRadixClients(shards)
			.flatMapMaybe(client ->
				client.getStatus()
					.filter(status -> !status.equals(RadixClientStatus.FAILURE)
						&& !status.equals(RadixClientStatus.CLOSING))
					.map(status -> client)
					.firstOrError()
					.toMaybe()
					.onErrorComplete()
			)
			.zipWith(Observable.interval(delaySecs, TimeUnit.SECONDS), (c, t) -> c)
			.flatMapMaybe(client ->
				client.getUniverse()
					.doOnSuccess(cliUniverse -> {
						if (checkUniverse && !config.equals(cliUniverse)) {
							LOGGER.warn("{} has universe: {} but looking for {}",
								client, cliUniverse.getHash(), config.getHash());
						}
					})
					.flatMapMaybe(config -> {
						if (!checkUniverse || config.equals(this.config)) {
							return Maybe.just(client);
						} else {
							return Maybe.empty();
						}
					})
					.onErrorComplete()
			)
			.firstOrError();
	}

}
