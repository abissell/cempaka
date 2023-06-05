package com.abissell.fixbridge;

public sealed interface FixCharMappedEnum<E extends Enum<E>>
        extends FixMappedEnum<E> // Valhalla TODO: hoist primitive chars to FixField
        permits ExecType, ExecTransType, HandlInst, MDEntryType, OrdStatus, OrdType, Side, SubscriptionRequestType, TimeInForce {

    public char fieldVal();

    static <E extends Enum<E>> E fromFixChar(char c, E[] values) {
        if (c >= 48 && c < 58) { // 10 numeric characters
            return values[c - 48];
        } else if (c >= 65 && c < 91) { // 26 uppercase alpha characters
            return values[c - 55];
        } else if (c >= 97 && c < 123) { // 26 lowercase alpha characters
            return values[c - 61];
        }

        throw new IllegalArgumentException("Cannot convert char " + c + " from FIX char field!");
    }
}
