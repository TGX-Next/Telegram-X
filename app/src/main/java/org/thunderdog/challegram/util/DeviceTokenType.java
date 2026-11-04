/*
 * This file is a part of Telegram X
 * Copyright © 2014 (tgx-android@pm.me)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * File created on 01/06/2023
 */
package org.thunderdog.challegram.util;

import androidx.annotation.IntDef;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.SOURCE)
@IntDef({
  DeviceTokenType.FIREBASE_CLOUD_MESSAGING,
  DeviceTokenType.SIMPLE_PUSH_SERVICE
})
// @MadeByLLM("gpt-6")
public @interface DeviceTokenType {
  // Value 1 is retired; persisted token types must retain their IDs.
  int
    FIREBASE_CLOUD_MESSAGING = 0,
    SIMPLE_PUSH_SERVICE = 2;
}
