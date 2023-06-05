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

public enum RiskLimit {
    APPROVED,
    TRADING_HALTED,
    CIRCUIT_BREAKER,
    MAG_EMPTY,
    MAX_LOSS,
    TRADEABLE_CONCURRENT_ENTRIES,
    SYSTEM_CONCURRENT_ENTRIES,
    BACKOFF_INTERVAL,
    TRADE_THEO_VAL,
    CROSS_RATIO,
    UNHEALTHY_QUEUE
}
