package com.abissell.fixbridge;

public /* primitive */ record PossResend(boolean flag) implements FixBool {
    private static final PossResend ORIG_TRANSMISSION = new PossResend(false);
    private static final PossResend POSS_RESEND = new PossResend(true);

    @Override
    public boolean fieldVal() {
        return flag;
    }

    @Override
    public FixField field() {
        return FixField.POSS_RESEND;
    }

    public static PossResend from(boolean flag) {
        return flag ? POSS_RESEND : ORIG_TRANSMISSION;
    }
}
