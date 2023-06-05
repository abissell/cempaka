package com.abissell.fixbridge;

// Keeping non-sealed to allow for alternative implementations
public non-sealed interface FixString extends FixFieldVal {

    public String fieldVal();
}
