package com.abissell.fixbridge;

public /* value */ record StrOrigClOrdID(String id) implements OrigClOrdID {
    @Override
    public String fieldVal() {
        return id();
    }
}
