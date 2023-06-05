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
package com.abissell.cempaka.util;

import java.util.Arrays;
import java.util.EnumSet;

import com.abissell.logutil.Log;
import com.abissell.cempaka.data.CcyPair;

public enum CempakaIOUtil {
    ; // Enum singleton
    public static EnumSet<CcyPair> readCcyPairInput(String purpose) {
        Log.INFO.to(DstSet.APP_STD_OUT, """
                                    Enter the space-separated CcyPairs to %s.
                                    For example: `NUMER_1_DENOM_1 NUMER_2_DENOM_2`
                                    Can also specify all pairs using `ALL`,
                                    or all NUMER_1 or NUMER_2 pairs using `NUMER_1` or `NUMER_2` respectively.);
                                    """.formatted(purpose));
        var ccyPairsStrs = System.console().readLine().trim();
        return switch (ccyPairsStrs) {
            case "ALL" -> EnumSet.allOf(CcyPair.class);
            case "NUMER_1" -> CcyPair.NUMER_1_PAIRS;
            case "NUMER_2" -> CcyPair.NUMER_2_PAIRS;
            default -> {
                var ccyPairsSet = EnumSet.noneOf(CcyPair.class);
                Arrays.stream(ccyPairsStrs.split(" "))
                        .map(CcyPair::valueOf).forEach(ccyPairsSet::add);
                yield ccyPairsSet;
            }
        };
    }
}
