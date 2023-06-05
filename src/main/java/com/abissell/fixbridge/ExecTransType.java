package com.abissell.fixbridge;

public enum ExecTransType implements FixCharMappedEnum<ExecTransType> {
    NEW('0'),
    CANCEL('1'),
    CORRECT('2'),
    STATUS('3');

    public final char fixChar;

    ExecTransType(char fixChar) {
        this.fixChar = fixChar;
    }

    public char fieldVal() {
        return fixChar;
    }

    public FixField field() {
        return FixField.EXEC_TRANS_TYPE;
    }

    private static final ExecTransType[] LOOKUP_TABLE = ExecTransType.values();

    public static ExecTransType fromFixChar(char c) {
        return FixCharMappedEnum.fromFixChar(c, LOOKUP_TABLE);
    }
}
