package com.abissell.fixbridge;

public sealed interface FixBool extends FixFieldVal
        permits PossDupFlag, PossResend {

    boolean fieldVal();
}
