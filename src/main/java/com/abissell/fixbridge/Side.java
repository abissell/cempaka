package com.abissell.fixbridge;

public enum Side implements FixCharMappedEnum<Side> {
    INDEX_0_DO_NOT_USE('0'),
    BUY('1'),
    SELL('2'),
    BUY_MINUS('3'),
    SELL_PLUS('4'),
    SELL_SHORT('5'),
    SELL_SHORT_EXEMPT('6'),
    UNDISCLOSED('7'),
    CROSS('8'),
    CROSS_SHORT('9'),
    CROSS_SHORT_EXEMPT('A'),
    AS_DEFINED('B'),
    OPPOSITE('C'),
    SUBSCRIBE('D'),
    REDEEM('E'),
    LEND('F'),
    BORROW('G');

    public final char fixChar;

    Side(char fixChar) {
        this.fixChar = fixChar;
    }

    @Override
    public char fieldVal() {
        return fixChar;
    }

    @Override
    public FixField field() {
        return FixField.SIDE;
    }

    private static final Side[] LOOKUP_TABLE = Side.values();

    public static Side fromFixChar(char c) {
        return FixCharMappedEnum.fromFixChar(c, LOOKUP_TABLE);
    }
}
