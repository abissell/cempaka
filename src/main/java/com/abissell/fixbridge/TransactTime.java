package com.abissell.fixbridge;

import java.time.LocalDateTime;

public /* value */ record TransactTime(LocalDateTime time) implements FixUtcTimestamp {

    @Override
    public LocalDateTime fieldVal() {
        return time;
    }

    @Override
    public FixField field() {
        return FixField.TRANSACT_TIME;
    }

    @Override
    public String toString() {
        return time.toString();
    }
}
