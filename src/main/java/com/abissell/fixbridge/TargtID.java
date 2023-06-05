package com.abissell.fixbridge;

public /* value */ record TargtID(String id) implements TargetCompID {
    @Override
    public String fieldVal() {
        return id;
    }
}
