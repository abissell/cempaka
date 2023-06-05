package com.abissell.fixbridge;

import com.abissell.javautil.io.ThreadLocalFormat;

public sealed interface FixDouble extends FixFieldVal
        permits AvgPx, CumQty, LastPx, LastShares, LeavesQty, MDEntryPx, MDEntrySize, MinQty, Price, OrderQty {

    double fieldVal();

    default String decimalString() {
        var decimalFormat = ThreadLocalFormat.with8SigDigits();
        return decimalFormat.format(fieldVal());
    }
}
