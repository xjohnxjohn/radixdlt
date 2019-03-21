package com.radix.regression.doublespend;

import com.radixdlt.client.core.Bootstrap;
import com.radixdlt.client.core.RadixUniverse;
import org.junit.BeforeClass;
import org.junit.Test;

public class DoubleSpendTokenTransferTest {
	@BeforeClass
	public static void setup() {
		if (!RadixUniverse.isInstantiated()) {
			RadixUniverse.bootstrap(Bootstrap.BETANET);
		}
	}

	@Test
	public void given_an_account_with_a_josh_token_with_one_supply__when_the_account_executes_two_transfers_via_two_different_nodes_at_the_same_time__then_the_account_balances_should_resolve_to_only_one_transfer() {
		DoubleSpendTestRunner testRunner = new DoubleSpendTestRunner(DoubleSpendTokenTransferTestConfig::new);
		testRunner.execute(10);
	}
}
