package com.ledger.api.domain;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class TransactionStateMachine {
    private static final Map<TransactionStatusType, Set<TransactionStatusType>> VALID_TRANSITIONS = new EnumMap<>(TransactionStatusType.class);

    static {
        VALID_TRANSITIONS.put(TransactionStatusType.PENDING, Set.of(
            TransactionStatusType.PROCESSING,
            TransactionStatusType.FAILED
        ));
        VALID_TRANSITIONS.put(TransactionStatusType.PROCESSING, Set.of(
            TransactionStatusType.SETTLED,
            TransactionStatusType.FAILED
        ));
        VALID_TRANSITIONS.put(TransactionStatusType.SETTLED, Set.of(
            TransactionStatusType.REVERSED
        ));
        VALID_TRANSITIONS.put(TransactionStatusType.FAILED, new HashSet<>()); // terminal
        VALID_TRANSITIONS.put(TransactionStatusType.REVERSED, new HashSet<>()); // terminal
    }

    public static void assertValidTransition(TransactionStatusType from, TransactionStatusType to) {
        if (!VALID_TRANSITIONS.getOrDefault(from, new HashSet<>()).contains(to)) {
            throw new InvalidStateTransitionException(
                "Invalid state transition from " + from + " to " + to
            );
        }
    }

    public static class InvalidStateTransitionException extends RuntimeException {
        public InvalidStateTransitionException(String message) {
            super(message);
        }
    }
}
