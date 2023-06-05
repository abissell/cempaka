package com.abissell.fixbridge;

public enum TimeInForce implements FixCharMappedEnum<TimeInForce> {
    DAY('0'),
    GOOD_TILL_CANCEL('1'),
    AT_THE_OPENING('2'),
    IMMEDIATE_OR_CANCEL('3'),
    FILL_OR_KILL('4'),
    GOOD_TILL_CROSSING('5'),
    GOOD_TILL_DATE('6'),
    AT_THE_CLOSE('7'),
    GOOD_THROUGH_CROSSING('8');

    public final char fixChar;

    TimeInForce(char fixChar) {
        this.fixChar = fixChar;
    }

    @Override
    public char fieldVal() {
        return fixChar;
    }

    @Override
    public FixField field() {
        return FixField.TIME_IN_FORCE;
    }

    private static final TimeInForce[] LOOKUP_TABLE = TimeInForce.values();

    public static TimeInForce fromFixChar(char c) {
        return FixCharMappedEnum.fromFixChar(c, LOOKUP_TABLE);
    }
}
