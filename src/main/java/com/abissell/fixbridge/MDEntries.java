package com.abissell.fixbridge;

import java.util.List;

public /* value */ record MDEntries(List<MDEntry> entries) implements FixGroup<MDEntry> {
    @Override
    public FixField field() {
        return FixField.MD_ENTRIES;
    }
}
