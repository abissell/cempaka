package com.abissell.fixbridge;

import com.abissell.javautil.rusty.ErrType;
import com.abissell.javautil.rusty.Result;

public interface ExecSessionBridge<R, E extends ErrType<E>> extends FixSessionBridge<R, E> {
    Result<R, E> sendNewOrderSingle(Order order);
    Result<R, E> sendOrderCancelRequest(OrdrCxlReq request);
}
