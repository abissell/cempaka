package com.abissell.fixbridge;

public sealed interface FixMappedEnum<E extends Enum<E>>
        extends FixFieldVal
        permits FixCharMappedEnum, FixStrMappedEnum {
}
