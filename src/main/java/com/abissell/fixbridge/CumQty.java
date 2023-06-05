package com.abissell.fixbridge;

public /* primitive */ record CumQty(double qty) implements FixDouble {
    @Override
    public double fieldVal() {
        return qty;
    }

    @Override
    public FixField field() {
        return FixField.CUM_QTY;
    }


    @Override
    public String toString() {
        return decimalString();
    }
}
