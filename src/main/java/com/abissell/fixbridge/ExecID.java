package com.abissell.fixbridge;

public /* value */ record ExecID(String id) implements FixString {

    @Override
    public String fieldVal() {
        return id;
    }

    @Override
    public FixField field() {
        return FixField.EXEC_ID;
    }
}
