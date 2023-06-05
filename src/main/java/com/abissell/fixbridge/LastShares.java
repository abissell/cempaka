package com.abissell.fixbridge;

public /* primitive */ record LastShares(double qty) implements FixDouble {
    @Override
    public double fieldVal() {
        return qty;
    }

    @Override
    public FixField field() {
        return FixField.LAST_SHARES;
    }

    @Override
    public String toString() {
        return decimalString();
    }
}
