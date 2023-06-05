package com.abissell.fixbridge;

public /* primitive */ record AvgPx(double px) implements FixDouble {
    @Override
    public double fieldVal() {
        return px;
    }

    @Override
    public FixField field() {
        return FixField.AVG_PX;
    }

    @Override
    public String toString() {
        return decimalString();
    }
}
