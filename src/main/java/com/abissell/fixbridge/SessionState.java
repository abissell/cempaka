package com.abissell.fixbridge;

import com.abissell.fixbridge.SessionState.Active;
import com.abissell.fixbridge.SessionState.Inactive;

public sealed interface SessionState permits Active, Inactive {
    boolean active();

    record Active<T extends SessionDetails>(T sessionDetails) implements SessionState {
        public boolean active() {
            return true;
        }
    }
    record Inactive<T extends SessionDetails>() implements SessionState {
        private static final Inactive<?> INACTIVE = new Inactive<>();

        public boolean active() {
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    static <T extends SessionDetails> SessionState.Inactive<T> inactive() {
        return (SessionState.Inactive<T>) Inactive.INACTIVE;
    }
}
