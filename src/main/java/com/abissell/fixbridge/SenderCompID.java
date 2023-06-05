package com.abissell.fixbridge;

public interface SenderCompID extends FixString {
    @Override
    default FixField field() {
        return FixField.SENDER_COMP_ID;
    }
}
