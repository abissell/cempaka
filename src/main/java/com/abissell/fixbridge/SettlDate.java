package com.abissell.fixbridge;

// This field could be written to use java.time.LocalDate, but keeping it as a
// raw String allows clients to elect whether to perform the conversion.
public /* value */ record SettlDate(StrDate date) implements FixStrDate {

    @Override
    public StrDate fieldVal() {
        return date;
    }

    @Override
    public FixField field() {
        return FixField.SETTL_DATE;
    }
}
