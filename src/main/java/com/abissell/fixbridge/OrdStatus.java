package com.abissell.fixbridge;

public enum OrdStatus implements FixCharMappedEnum<OrdStatus> {
    NEW('0'),
    PARTIALLY_FILLED('1'),
    FILLED('2'),
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
    ACCEPTED_FOR_BIDDING('D'),
    PENDING_REPLACE('E');

    public final char fixChar;

    OrdStatus(char fixChar) {
        this.fixChar = fixChar;
    }

    @Override
    public char fieldVal() {
        return fixChar;
    }

    @Override
    public FixField field() {
        return FixField.ORD_STATUS;
    }

    private static final OrdStatus[] LOOKUP_TABLE = OrdStatus.values();

    public static OrdStatus fromFixChar(char c) {
        return FixCharMappedEnum.fromFixChar(c, LOOKUP_TABLE);
    }
}
