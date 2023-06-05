package com.abissell.fixbridge;

public /* value */ record MDReqID(String id) implements FixString {

    @Override
    public String fieldVal() {
        return id;
    }

    @Override
    public FixField field() {
        return FixField.MD_REQ_ID;
    }
}
