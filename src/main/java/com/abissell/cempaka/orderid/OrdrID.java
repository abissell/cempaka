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
package com.abissell.cempaka.orderid;

import java.time.LocalDateTime;

public /* primitive */ record OrdrID(
        byte b1,
        byte b2,
        byte b3,
        byte b4,
        byte b5,
        byte b6,
        byte b7,
        byte b8) {

    public OrdrID {
        validate(b1);
        validate(b2);
        validate(b3);
        validate(b4);
        validate(b5);
        validate(b6);
        validate(b7);
        validate(b8);
    }

    private void validate(byte b) {
        if (!isValid(b)) {
            throw new IllegalArgumentException(
                    "Tried to create ClientOrderID with invalid char " + (char) b);
        }
    }

    private static boolean isValid(byte b) {
        if (b >= 97 && b < 123) { // 26 lowercase alpha characters
            return true;
        }
        if (b >= 65 && b < 91) { // 26 uppercase alpha characters
            return true;
        }
        // 10 numeric characters
        return b >= 48 && b < 58;
    }

    public static boolean isValidInput(String s) {
        if (s.length() != 8) {
            return false;
        }

        return isValid((byte) s.charAt(0))
                && isValid((byte) s.charAt(1))
                && isValid((byte) s.charAt(2))
                && isValid((byte) s.charAt(3))
                && isValid((byte) s.charAt(4))
                && isValid((byte) s.charAt(5))
                && isValid((byte) s.charAt(6))
                && isValid((byte) s.charAt(7));
    }

    public static OrdrID from(String s) {
        return new OrdrID((byte) s.charAt(0), (byte) s.charAt(1),
                (byte) s.charAt(2), (byte) s.charAt(3), (byte) s.charAt(4),
                (byte) s.charAt(5), (byte) s.charAt(6), (byte) s.charAt(7));
    }

    public static OrdrID from(LocalDateTime dateTime) {
        return from(dateTime, 0);
    }

    public static OrdrID from(LocalDateTime dateTime, int incrementB7) {
        byte b1 = (byte) convertHour(dateTime.getHour());
        byte b2 = (byte) convertMinuteOrSecond(dateTime.getMinute());
        byte b3 = (byte) convertMinuteOrSecond(dateTime.getSecond());

        final int nanos = dateTime.getNano();
        byte b4 = convertByte((byte) ((nanos >> 24) & 0xff));
        byte b5 = convertByte((byte) ((nanos >> 16) & 0xff));
        byte b6 = convertByte((byte) ((nanos >> 8) & 0xff));
        byte b7 = convertByte((byte) (nanos & 0xff));

        if (incrementB7 > 0) {
            byte newB7 = convertByte((byte) (b7 + incrementB7));
            if (newB7 == b7) {
                newB7 = (byte) (newB7 + 1);
            }
            b7 = newB7;
        }

        byte b8 = (byte) '0';

        return new OrdrID(b1, b2, b3, b4, b5, b6, b7, b8);
    }

    private static char convertHour(int hour) {
        if (hour < 10) {
            return (char) (hour + 48);
        } else {
            return (char) ((hour - 10) + 97);
        }
    }

    private static char convertMinuteOrSecond(int value) {
        if (value < 10) {
            return (char) (value + 48);
        } else if (value < 36) {
            return (char) ((value - 10) + 97);
        } else {
            return (char) ((value - 36) + 65);
        }
    }

    private static final byte[] VALID_BYTES = new byte[62];
    static {
        char c = '0';
        for (int i = 0; i < 10; i++) {
            VALID_BYTES[i] = (byte) c++;
        }
        c = 'a';
        for (int i = 10; i < (10 + 26); i++) {
            VALID_BYTES[i] = (byte) c++;
        }
        c = 'A';
        for (int i = (10 + 26); i < (10 + 26 + 26); i++) {
            VALID_BYTES[i] = (byte) c++;
        }
    }

    private static byte convertByte(byte b) {
        if (isValid(b)) {
            return b;
        }

        if (b < 0) {
            if (b > -128) {
                b = (byte) (-1 * b);
            } else {
                b = 127;
            }
        }

        final byte normalized;
        if (b < 48) {
            normalized = b;
        } else if (b < 65) {
            normalized = (byte) (b - 10);
        } else if (b < 97) {
            normalized = (byte) (b - 36);
        } else {
            normalized = (byte) (b - 52);
        }

        int index = normalized % 52;

        return VALID_BYTES[index];
    }

    public String asStr() {
        char[] arr = new char[8];
        arr[0] = (char) b1;
        arr[1] = (char) b2;
        arr[2] = (char) b3;
        arr[3] = (char) b4;
        arr[4] = (char) b5;
        arr[5] = (char) b6;
        arr[6] = (char) b7;
        arr[7] = (char) b8;
        return new String(arr);
    }

    public OrdrID getCxlReqID() {
        return new OrdrID(b1, b2, b3, b4, b5, b6, b7, (byte) 'X');
    }

    @Override
    public String toString() {
        return "OrdrID[" + asStr() + "]";
    }
}
