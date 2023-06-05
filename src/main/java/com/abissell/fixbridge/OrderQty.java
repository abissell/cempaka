package com.abissell.fixbridge;

public /* primitive */ record OrderQty(double qty) implements FixDouble {
    public OrderQty {
        if (qty <= 0.0d) {
            throw new IllegalArgumentException("negative qty! " + qty);
        }
    }

    @Override
    public double fieldVal() {
        return qty;
    }

    @Override
    public FixField field() {
        return FixField.ORDER_QTY;
    }
}
