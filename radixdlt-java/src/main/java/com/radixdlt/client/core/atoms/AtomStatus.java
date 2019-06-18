package com.radixdlt.client.core.atoms;

/**
 * The different states an atom can be in.
 */
public enum AtomStatus {
	DOES_NOT_EXIST,
	EVICTED_FAILED_CM_VERIFICATION,
	EVICTED_CONFLICT_LOSER,
	PENDING_CM_VERIFICATION,
	PENDING_DEPENDENCY_VERIFICATION,
	MISSING_DEPENDENCY,
	CONFLICT_LOSER,
	STORED
}
