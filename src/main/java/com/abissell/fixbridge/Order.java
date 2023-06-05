package com.abissell.fixbridge;

public /* value */ record Order(
        ClOrdID id,
        String idStr,
        Tradeable tradeable,
        FixString base,
        Side side,
        OrderQty qty,
        Price px,
        TimeInForce timeInForce,
        TransactTime sentTime) {

    public Order(ClOrdID ordID, Tradeable tradeable, FixString base, Side side, OrderQty qty,
            Price px, TimeInForce timeInForce, TransactTime sentTime) {
        this(ordID, ordID.fieldVal(), tradeable, base, side, qty, px, timeInForce, sentTime);
    }

    public Order withNewQty(OrderQty newQty) {
        return new Order(id, idStr, tradeable, base, side, newQty, px, timeInForce, sentTime);
    }
}
