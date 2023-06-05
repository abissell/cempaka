package com.abissell.fixbridge;

public sealed interface OrdID extends FixString
    permits ClOrdID, OrigClOrdID {
}
