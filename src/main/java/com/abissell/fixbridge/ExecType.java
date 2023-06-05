package com.abissell.fixbridge;

public enum ExecType implements FixCharMappedEnum<ExecType> {
    NEW('0'),
    PARTIAL_FILL('1'),
    FILL('2'),
    DONE_FOR_DAY('3'),
    CANCELED('4'),
    REPLACED('5'),
    PENDING_CANCEL('6'),
    STOPPED('7'),
    REJECTED('8'),
    SUSPENDED('9'),
    PENDING_NEW('A'),
    CALCULATED('B'),
    EXPIRED('C'),
    RESTATED('D'),
    PENDING_REPLACE('E'),
    TRADE('F'),
    TRADE_CORRECT('G'),
    TRADE_CANCEL('H'),
    ORDER_STATUS('I'),
    TRADE_IN_A_CLEARING_HOLD('J'),
    TRADE_HAS_BEEN_RELEASED_TO_CLEARING('K'),
    TRIGGERED_OR_ACTIVATED_BY_SYSTEM('L');

    public final char fixChar;

    ExecType(char fixChar) {
        this.fixChar = fixChar;
    }

    public FixField field() {
        return FixField.EXEC_TYPE;
    }

    public char fieldVal() {
        return fixChar;
    }

    private static final ExecType[] LOOKUP_TABLE = ExecType.values();

    public static ExecType fromFixChar(char c) {
        return FixCharMappedEnum.fromFixChar(c, LOOKUP_TABLE);
    }
}
