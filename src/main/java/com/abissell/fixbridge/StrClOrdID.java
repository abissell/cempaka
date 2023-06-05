package com.abissell.fixbridge;

public /* value */ record StrClOrdID(String id) implements ClOrdID {
    @Override
    public String fieldVal() {
        return id();
    }
}
