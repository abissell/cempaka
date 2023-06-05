/*
 * cempaka, an algorithmic trading platform written in Java
 * Copyright (C) 2023 Andrew Bissell
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.

 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.abissell.cempaka.data;

import com.abissell.fixbridge.CachedTradeable;
import com.abissell.fixbridge.FixField;

import java.util.Arrays;
import java.util.EnumSet;

public enum CcyPair implements CachedTradeable<CcyPair> {
    NUMER_1_DENOM_1(Ccy.NUMER_1, Ccy.DENOM_1),
    NUMER_1_DENOM_2(Ccy.NUMER_1, Ccy.DENOM_2),
    NUMER_1_DENOM_3(Ccy.NUMER_1, Ccy.DENOM_3),

    NUMER_2_DENOM_1(Ccy.NUMER_2, Ccy.DENOM_1),
    NUMER_2_DENOM_2(Ccy.NUMER_2, Ccy.DENOM_2),
    NUMER_2_DENOM_3(Ccy.NUMER_2, Ccy.DENOM_3);

    public static final EnumSet<CcyPair> NUMER_1_PAIRS;
    static {
        final var numer1Pairs = EnumSet.noneOf(CcyPair.class);
        Arrays.stream(CcyPair.values()).filter(p -> p.ccy1 == Ccy.NUMER_1).forEach(numer1Pairs::add);
        NUMER_1_PAIRS = numer1Pairs;
    }

    public static final EnumSet<CcyPair> NUMER_2_PAIRS;
    static {
        final var numer2Pairs = EnumSet.noneOf(CcyPair.class);
        Arrays.stream(CcyPair.values()).filter(p -> p.ccy1 == Ccy.NUMER_2).forEach(numer2Pairs::add);
        NUMER_2_PAIRS = numer2Pairs;
    }

    public final Ccy ccy1;
    public final Ccy ccy2;
    public final String fixStr;

    CcyPair(Ccy ccy1, Ccy ccy2) {
        this.ccy1 = ccy1;
        this.ccy2 = ccy2;
        this.fixStr = ccy1.fieldVal() + "/" + ccy2.fieldVal();
    }

    @Override
    public String fieldVal() {
        return fixStr;
    }

    @Override
    public FixField field() {
        return FixField.SYMBOL;
    }

    @Override
    public String symbol() {
        return fixStr;
    }

    //@formatter:off
    // TODO: Make this correct
    public static CcyPair from(String str) {
        return NUMER_1_DENOM_1;
    }
    //@formatter:on
}
