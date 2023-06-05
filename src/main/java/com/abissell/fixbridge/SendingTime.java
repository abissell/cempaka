package com.abissell.fixbridge;

import java.time.LocalDateTime;

public /* value */ record SendingTime(LocalDateTime sendingTime) implements FixUtcTimestamp {

    @Override
    public LocalDateTime fieldVal() {
        return sendingTime;
    }

    @Override
    public FixField field() {
        return FixField.SENDING_TIME;
    }
}
