package com.abissell.fixbridge;

public /* value */ record NoMDEntries(int noMDEntries) implements FixInt {

    @Override
    public int fieldVal() {
        return noMDEntries;
    }

    @Override
    public FixField field() {
        return FixField.NO_MD_ENTRIES;
    }
}
