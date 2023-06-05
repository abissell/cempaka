package com.abissell.fixbridge;

import java.util.EnumMap;
import java.util.function.Function;

public enum HandlInst implements FixCharMappedEnum<HandlInst> {
    INDEX_0_DO_NOT_USE('0'),
    AUTOMATED_EXECUTION_ORDER_PRIVATE_NO_BROKER_INTERVENTION('1'),
    AUTOMATED_EXECUTION_ORDER_PUBLIC_BROKER_INTERVENTION_OK('2'),
    MANUAL_ORDER_BEST_EXECUTION('3');

    public final char fixChar;

    HandlInst(char fixChar) {
        this.fixChar = fixChar;
    }

    @Override
    public char fieldVal() {
        return fixChar;
    }

    @Override
    public FixField field() {
        return FixField.HANDL_INST;
    }

    private static final HandlInst[] LOOKUP_TABLE = HandlInst.values();

    public static HandlInst fromFixChar(char c) {
        return FixCharMappedEnum.fromFixChar(c, LOOKUP_TABLE);
    }

    public static <V> EnumMap<HandlInst, V> buildCache(Function<HandlInst, V> builder) {
        return FixField.buildEnumCache(
                HandlInst.values(),
                builder,
                () -> new EnumMap<>(HandlInst.class)
                );
    }
}
