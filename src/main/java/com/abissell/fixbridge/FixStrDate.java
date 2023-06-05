package com.abissell.fixbridge;

public sealed interface FixStrDate extends FixFieldVal permits SettlDate {

    StrDate fieldVal();
}
