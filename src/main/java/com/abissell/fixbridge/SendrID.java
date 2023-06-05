package com.abissell.fixbridge;

public /* value */ record SendrID(String id) implements SenderCompID {
    @Override
    public String fieldVal() {
        return id;
    }
}
