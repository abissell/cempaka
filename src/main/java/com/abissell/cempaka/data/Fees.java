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

import com.abissell.fixbridge.Side;

public interface Fees {
    double feesChg(double qtyChg, double valChg);
    double adjustedPx(Side side, double px);

    Fees ZERO = new Fees() {
        @Override
        public double feesChg(double qtyChg, double valChg) {
            return 0.0d;
        }

        @Override
        public double adjustedPx(Side side, double px) {
            return px;
        }
    };

    Fees PCT = new Fees() {
        private static final double FEE_RATE = 0.00005d;

        @Override
        public double feesChg(double qtyChg, double valChg) {
            return valChg * FEE_RATE;
        }

        @Override
        public double adjustedPx(Side side, double px) {
            return switch (side) {
                case SELL -> px * (1.0d + FEE_RATE);
                case BUY -> px * (1.0d - FEE_RATE);
                default -> throw new IllegalArgumentException("" + side);
            };
        }
    };
}
