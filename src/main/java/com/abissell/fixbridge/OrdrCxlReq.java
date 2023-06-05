package com.abissell.fixbridge;

public /* value */ record OrdrCxlReq(
        ClOrdID id,
        String idStr,
        Order order,
        TransactTime sentTime) {
    public OrdrCxlReq(ClOrdID id, Order order, TransactTime sentTime) {
        this(id, id.fieldVal(), order, sentTime);
    }
}
