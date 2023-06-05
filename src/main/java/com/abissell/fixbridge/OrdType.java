package com.abissell.fixbridge;

public enum OrdType implements FixCharMappedEnum<OrdType> {
    INDEX_0_DO_NOT_USE('0'),
    MARKET('1'),
    LIMIT('2'),
    STOP_STOP_LOSS('3'),
    STOP_LIMIT('4'),
    MARKET_ON_CLOSE('5'),
    WITH_OR_WITHOUT('6'),
    LIMIT_OR_BETTER('7'),
    LIMIT_WITH_OR_WITHOUT('8'),
    ON_BASIS('9'),
    ON_CLOSE('A'),
    LIMIT_ON_CLOSE('B'),
    FOREX_MARKET('C'),
    PREVIOUSLY_QUOTED('D'),
    PREVIOUSLY_INDICATED('E'),
    FOREX_LIMIT('F'),
    FOREX_SWAP('G'),
    FOREX_PREVIOUSLY_QUOTED('H'),
    FUNARI('I'),
    MARKET_IF_TOUCHED('J'),
    MARKET_WITH_LEFT_OVER_AS_LIMIT('K'),
    PREVIOUS_FUND_VALUATION_POINT('L'),
    NEXT_FUND_VALUATION_POINT('M'),
    INDEX_N_DO_NOT_USE('N'),
    INDEX_O_DO_NOT_USE('O'),
    PEGGED('P'),
    COUNTER_ORDER_SELECTION('Q');

    public final char fixChar;

    OrdType(char fixChar) {
        this.fixChar = fixChar;
    }

    @Override
    public char fieldVal() {
        return fixChar;
    }

    @Override
    public FixField field() {
        return FixField.ORD_TYPE;
    }

    private static final OrdType[] LOOKUP_TABLE = OrdType. values();

    public static OrdType fromFixChar(char c) {
        return FixCharMappedEnum.fromFixChar(c, LOOKUP_TABLE);
    }
}
