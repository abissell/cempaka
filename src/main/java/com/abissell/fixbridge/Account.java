package com.abissell.fixbridge;

public /* value */ record Account(String acct) implements FixString {

    @Override
    public String fieldVal() {
        return acct;
    }

    @Override
    public FixField field() {
        return FixField.ACCOUNT;
    }
}
