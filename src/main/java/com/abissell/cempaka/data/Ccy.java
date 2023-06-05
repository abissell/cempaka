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

import com.abissell.fixbridge.FixField;
import com.abissell.fixbridge.FixStrMappedEnum;

public enum Ccy implements FixStrMappedEnum<Ccy> {
    NUMER_1("NUMER_1"),
    NUMER_2("NUMER_2"),

    DENOM_1("DENOM_1"),
    DENOM_2("DENOM_2"),
    DENOM_3("DENOM_3");

    public final String fixStr;

    Ccy(String fixStr) {
        this.fixStr = fixStr;
    }

    @Override
    public String fieldVal() {
        return fixStr;
    }

    @Override
    public FixField field() {
        return FixField.CURRENCY;
    }

    //@formatter:off
    // TODO: Make this correct
    public static Ccy fromFixStr(String str) {
        return NUMER_1;
    }
    //@formatter:on
}
