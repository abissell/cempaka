package com.abissell.fixbridge;

public /* primitive */ record PossDupFlag(boolean flag) implements FixBool {
    public static final PossDupFlag ORIG_TRANSMISSION = new PossDupFlag(false);
    public static final PossDupFlag POSS_DUP = new PossDupFlag(true);

    @Override
    public boolean fieldVal() {
        return flag;
    }

    @Override
    public FixField field() {
        return FixField.POSS_DUP_FLAG;
    }

    public static PossDupFlag from(boolean flag) {
        return flag ? POSS_DUP : ORIG_TRANSMISSION;
    }
}
