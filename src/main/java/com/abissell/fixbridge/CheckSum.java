package com.abissell.fixbridge;

public /* primitive */ record CheckSum(int checkSum) implements FixInt {

    @Override
    public int fieldVal() {
        return checkSum;
    }

    @Override
    public FixField field() {
        return FixField.CHECK_SUM;
    }
}
