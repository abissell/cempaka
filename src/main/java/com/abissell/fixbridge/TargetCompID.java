package com.abissell.fixbridge;

public interface TargetCompID extends FixString {
    @Override
    default FixField field() {
        return FixField.TARGET_COMP_ID;
    }
}
