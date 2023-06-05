package com.abissell.fixbridge;

public enum MDEntryType implements FixCharMappedEnum<MDEntryType> {
    BID('0'),
    OFFER('1'),
    TRADE('2'),
    INDEX_VALUE('3'),
    OPENING_PRICE('4'),
    CLOSING_PRICE('5'),
    SETTLEMENT_PRICE('6'),
    TRADING_SESSION_HIGH_PRICE('7'),
    TRADING_SESSION_LOW_PRICE('8'),
    TRADING_SESSION_VWAP_PRICE('9'),
    IMBALANCE('A'),
    TRADE_VOLUME('B'),
    OPEN_INTEREST('C');

    public final char fixChar;

    MDEntryType(char fixChar) {
        this.fixChar = fixChar;
    }

    public FixField field() {
        return FixField.MD_ENTRY_TYPE;
    }

    public char fieldVal() {
        return fixChar;
    }

    private static final MDEntryType[] LOOKUP_TABLE = MDEntryType.values();

    public static MDEntryType fromFixChar(char c) {
        return FixCharMappedEnum.fromFixChar(c, LOOKUP_TABLE);
    }
}
