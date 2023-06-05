package com.abissell.fixbridge;

public /* primitive */ record LastPx(double px) implements FixDouble {
    @Override
    public double fieldVal() {
        return px;
    }

    @Override
    public FixField field() {
        return FixField.LAST_PX;
    }

    @Override
    public String toString() {
        return decimalString();
    }
}
