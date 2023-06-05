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

import java.util.Collections;
import java.util.List;

public /* value */ record CxdBookAnalysis(
        List<CxdBookLvl> cxdBidLvls,
        List<CxdBookLvl> cxdAskLvls,
        double theoValUsd) {

    public static final CxdBookAnalysis NOT_CXD =
        new CxdBookAnalysis(
                Collections.emptyList(),
                Collections.emptyList(),
                0.0d
            );
}
