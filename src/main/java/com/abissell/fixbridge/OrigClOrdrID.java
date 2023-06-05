package com.abissell.fixbridge;

import com.abissell.cempaka.orderid.OrdrID;

public /* primitive */ record OrigClOrdrID(OrdrID id) implements OrigClOrdID {

    @Override
    public String fieldVal() {
        return id.asStr();
    }

    public static OrigClOrdrID from(String s) {
        return new OrigClOrdrID(OrdrID.from(s));
    }
}
