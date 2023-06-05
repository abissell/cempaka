package com.abissell.fixbridge;

public /* primitive */ record MinQty(double minQty) implements FixDouble {

    @Override
    public double fieldVal() {
        return minQty;
    }

    @Override
    public FixField field() {
        return FixField.MIN_QTY;
    }
}
