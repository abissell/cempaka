package com.abissell.fixbridge;

public non-sealed interface OrigClOrdID extends OrdID {
    @Override
    default FixField field() {
        return FixField.ORIG_CL_ORD_ID;
    }
}
