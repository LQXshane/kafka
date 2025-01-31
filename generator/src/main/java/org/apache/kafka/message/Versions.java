/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.kafka.message;

import java.util.Objects;

/**
 * A version range.
 *
 * A range consists of two 16-bit numbers: the lowest version which is accepted, and the highest.
 * Ranges are inclusive, meaning that both the lowest and the highest version are valid versions.
 * The only exception to this is the NONE range, which contains no versions at all.
 *
 * Version ranges can be represented as strings.
 *
 * A single supported version V is represented as "V".
 * A bounded range from A to B is represented as "A-B".
 * All versions greater than A is represented as "A+".
 * The NONE range is represented as the string "none".
 */
public final class Versions {
    private final short lowest;
    private final short highest;

    public static Versions parse(String input, Versions defaultVersions) {
        if (input == null) {
            return defaultVersions;
        }
        String trimmedInput = input.trim();
        if (trimmedInput.length() == 0) {
            return defaultVersions;
        }
        if (trimmedInput.equals(NONE_STRING)) {
            return NONE;
        }
        if (trimmedInput.endsWith("+")) {
            return new Versions(Short.parseShort(
            trimmedInput.substring(0, trimmedInput.length() - 1)),
                Short.MAX_VALUE);
        } else {
            int dashIndex = trimmedInput.indexOf("-");
            if (dashIndex < 0) {
                short version = Short.parseShort(trimmedInput);
                return new Versions(version, version);
            }
            return new Versions(
                Short.parseShort(trimmedInput.substring(0, dashIndex)),
                Short.parseShort(trimmedInput.substring(dashIndex + 1)));
        }
    }

    public static final Versions ALL = new Versions((short) 0, Short.MAX_VALUE);

    public static final Versions NONE = new Versions();

    public static final String NONE_STRING = "none";

    private Versions() {
        this.lowest = 0;
        this.highest = -1;
    }

    public Versions(short lowest, short highest) {
        if ((lowest < 0) || (highest < 0)) {
            throw new RuntimeException("Invalid version range " +
                lowest + " to " + highest);
        }
        this.lowest = lowest;
        this.highest = highest;
    }

    public short lowest() {
        return lowest;
    }

    public short highest() {
        return highest;
    }

    public boolean empty() {
        return lowest > highest;
    }

    @Override
    public String toString() {
        if (empty()) {
            return NONE_STRING;
        } else if (lowest == highest) {
            return String.valueOf(lowest);
        } else if (highest == Short.MAX_VALUE) {
            return String.format("%d+", lowest);
        } else {
            return String.format("%d-%d", lowest, highest);
        }
    }

    /**
     * Return the intersection of two version ranges.
     *
     * @param other     The other version range.
     * @return          A new version range.
     */
    public Versions intersect(Versions other) {
        short newLowest = lowest > other.lowest ? lowest : other.lowest;
        short newHighest = highest < other.highest ? highest : other.highest;
        if (newLowest > newHighest) {
            return Versions.NONE;
        }
        return new Versions(newLowest, newHighest);
    }

    /**
     * Return a new version range that trims some versions from this range, if possible.
     * We can't trim any versions if the resulting range would be disjoint.
     *
     * Some examples:
     * 1-4.trim(1-2) = 3-4
     * 3+.trim(4+) = 3
     * 4+.trim(3+) = none
     * 1-5.trim(2-4) = null
     *
     * @param other                 The other version range.
     * @return                      A new version range.
     */
    public Versions subtract(Versions other) {
        if (other.lowest() <= lowest) {
            if (other.highest >= highest) {
                // Case 1: other is a superset of this.  Trim everything.
                return Versions.NONE;
            } else if (other.highest < lowest) {
                // Case 2: other is a disjoint version range that is lower than this.  Trim nothing.
                return this;
            } else {
                // Case 3: trim some values from the beginning of this range.
                //
                // Note: it is safe to assume that other.highest() + 1 will not overflow.
                // The reason is because if other.highest() were Short.MAX_VALUE,
                // other.highest() < highest could not be true.
                return new Versions((short) (other.highest() + 1), highest);
            }
        } else if (other.highest >= highest) {
            int newHighest = other.lowest - 1;
            if (newHighest < 0) {
                // Case 4: other was NONE.  Trim nothing.
                return this;
            } else if (newHighest < highest) {
                // Case 5: trim some values from the end of this range.
                return new Versions(lowest, (short) newHighest);
            } else {
                // Case 6: other is a disjoint range that is higher than this.  Trim nothing.
                return this;
            }
        } else {
            // Case 7: the difference between this and other would be two ranges, not one.
            return null;
        }
    }

    public boolean contains(short version) {
        return version >= lowest && version <= highest;
    }

    public boolean contains(Versions other) {
        if (other.empty()) {
            return true;
        }
        return !((lowest > other.lowest) || (highest < other.highest));
    }

    @Override
    public int hashCode() {
        return Objects.hash(lowest, highest);
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof Versions)) {
            return false;
        }
        Versions otherVersions = (Versions) other;
        return lowest == otherVersions.lowest &&
               highest == otherVersions.highest;
    }
}
