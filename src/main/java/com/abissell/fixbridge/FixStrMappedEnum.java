package com.abissell.fixbridge;

// Keeping this non-sealed allows clients to define new/multiple types occupying
// the same FIX fields, such as "Symbol <55>"
public non-sealed interface FixStrMappedEnum<E extends Enum<E>>
        extends FixMappedEnum<E>, FixString {

    public String fieldVal();
}
