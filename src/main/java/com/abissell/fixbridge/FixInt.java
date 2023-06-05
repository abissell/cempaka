package com.abissell.fixbridge;

public sealed interface FixInt extends FixFieldVal
        permits CheckSum, MarketDepth, MsgSeqNum, NoMDEntries {

    int fieldVal();
}
