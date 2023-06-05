package com.abissell.fixbridge;

public non-sealed interface ClOrdID extends OrdID {
    @Override
    default FixField field() {
        return FixField.CL_ORD_ID;
    }
}
