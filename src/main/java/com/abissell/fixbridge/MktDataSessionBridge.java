package com.abissell.fixbridge;

import java.util.Collection;

import com.abissell.javautil.rusty.ErrType;
import com.abissell.javautil.rusty.Result;

public interface MktDataSessionBridge<R, E extends ErrType<E>> extends FixSessionBridge<R, E> {
    Result<R, E> subscribe(MktDataSubscriptionReq request, Collection<? extends Ticker> tickers);
    Result<R, E> unsubscribe(MDReqID mdReqID, Collection<? extends Ticker> tickers);
}
