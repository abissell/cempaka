package com.abissell.fixbridge;

// TODO: Allow this to be generalized to Tradeables stored as Strings
public interface CachedTradeable<E extends Enum<E>>
    extends Tradeable, FixStrMappedEnum<E> {
}
