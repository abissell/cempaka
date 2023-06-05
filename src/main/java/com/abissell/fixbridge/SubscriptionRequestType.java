package com.abissell.fixbridge;

public enum SubscriptionRequestType implements FixCharMappedEnum<SubscriptionRequestType> {
    SNAPSHOT('0'),
    SUBSCRIBE('1'),
    UNSUBSCRIBE('2');

    public final char fixChar;

    SubscriptionRequestType(char fixChar) {
        this.fixChar = fixChar;
    }

    public FixField field() {
        return FixField.SUBSCRIPTION_REQUEST_TYPE;
    }

    public char fieldVal() {
        return fixChar;
    }

    private static final SubscriptionRequestType[] LOOKUP_TABLE = SubscriptionRequestType.values();

    public static SubscriptionRequestType fromFixChar(char c) {
        return FixCharMappedEnum.fromFixChar(c, LOOKUP_TABLE);
    }
}
