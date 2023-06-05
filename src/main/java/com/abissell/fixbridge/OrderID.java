package com.abissell.fixbridge;

public /* value */ record OrderID(String id) implements FixString {

    @Override
    public String fieldVal() {
        return id;
    }

    @Override
    public FixField field() {
        return FixField.ORDER_ID;
    }
}
