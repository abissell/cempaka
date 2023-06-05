package com.abissell.fixbridge;

public /* primitive */ record MDEntrySize(double size) implements FixDouble {
    @Override
    public double fieldVal() {
        return size;
    }

    @Override
    public FixField field() {
        return FixField.MD_ENTRY_SIZE;
    }

    @Override
    public String toString() {
        return decimalString();
    }
}
