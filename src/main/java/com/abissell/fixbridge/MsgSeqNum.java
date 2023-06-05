package com.abissell.fixbridge;

public /* primitive */ record MsgSeqNum(int seqNum) implements FixInt {

    @Override
    public int fieldVal() {
        return seqNum;
    }

    @Override
    public FixField field() {
        return FixField.MSG_SEQ_NUM;
    }
}
