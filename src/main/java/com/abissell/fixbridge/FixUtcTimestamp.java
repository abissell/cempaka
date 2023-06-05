package com.abissell.fixbridge;

import java.time.LocalDateTime;

// Keeping non-sealed to allow for alternative timestamp type implementations
public non-sealed interface FixUtcTimestamp extends FixFieldVal {

    public LocalDateTime fieldVal();
}
