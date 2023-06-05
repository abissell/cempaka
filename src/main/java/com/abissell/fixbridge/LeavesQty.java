package com.abissell.fixbridge;

public /* primitive */ record LeavesQty(double qty) implements FixDouble {
    @Override
    public double fieldVal() {
        return qty;
    }

    @Override
    public FixField field() {
        return FixField.LEAVES_QTY;
    }

    @Override
    public String toString() {
        return decimalString();
    }
}
