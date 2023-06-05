package com.abissell.fixbridge;

public /* primitive */ record MarketDepth(int depth) implements FixInt {

    @Override
    public int fieldVal() {
        return depth;
    }

    @Override
    public FixField field() {
        return FixField.MARKET_DEPTH;
    }
}
