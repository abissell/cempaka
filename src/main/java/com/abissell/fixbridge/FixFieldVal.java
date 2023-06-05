package com.abissell.fixbridge;

public sealed interface FixFieldVal
        permits FixMappedEnum, FixBool, FixInt, FixDouble, FixUtcTimestamp,
        FixString, FixStrDate, FixGroup {

    FixField field();

    default int fixTag() {
        return field().fixTag;
    }

    default String fixName() {
        return field().fixName;
    }

    default String loglinePrefix() { return field().loglinePrefix; }

    default String toLogline() { return loglinePrefix() + this + '\n'; }
}
