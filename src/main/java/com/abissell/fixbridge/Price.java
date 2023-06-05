package com.abissell.fixbridge;

public /* primitive */ record Price(double px) implements FixDouble {

    @Override
    public double fieldVal() {
        return px;
    }

    @Override
    public FixField field() {
        return FixField.PRICE;
    }
}
