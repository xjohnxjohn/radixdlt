package com.radixdlt.engine;

import com.google.common.collect.ImmutableMap;
import com.radixdlt.constraintmachine.CMAtom;
import com.radixdlt.constraintmachine.CMError;
import java.util.Set;

/**
 * Listener for atom events as they go through the Radix Engine pipeline.
 */
public interface AtomEventListener {
	default void onCMSuccess(CMAtom cmAtom, ImmutableMap<String, Object> computed) {
	}
	default void onCMError(CMAtom cmAtom, Set<CMError> errors) {
	}
}
