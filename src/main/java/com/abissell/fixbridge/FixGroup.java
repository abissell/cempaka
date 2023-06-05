package com.abissell.fixbridge;

public sealed interface FixGroup<T>
        extends FixFieldVal
        permits MDEntries {

}
