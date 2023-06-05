package com.abissell.fixbridge;

public /* primitive */ record MDEntryPx(double px) implements FixDouble {
    @Override
    public double fieldVal() {
        return px;
    }

    @Override
    public FixField field() {
        return FixField.MD_ENTRY_PX;
    }

    @Override
    public String toString() {
        return decimalString();
    }
}
