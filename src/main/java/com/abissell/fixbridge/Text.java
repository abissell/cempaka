package com.abissell.fixbridge;

public /* value */ record Text(String text) implements FixString {

    @Override
    public String fieldVal() {
        return text;
    }

    @Override
    public FixField field() {
        return FixField.TEXT;
    }
}
