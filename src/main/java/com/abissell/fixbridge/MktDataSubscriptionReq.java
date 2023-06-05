package com.abissell.fixbridge;

import com.abissell.javautil.rusty.Opt;

public /* value */ record MktDataSubscriptionReq(
        MDReqID mdReqID,
        SubscriptionRequestType subReqType,
        MarketDepth marketDepth,
        Opt<MinQty> minQty) { }
