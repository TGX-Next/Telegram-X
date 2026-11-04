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
 * File created on 10/07/2018
 */
// region @MadeByLLM("gpt-6", id = "android13-arm64")
package org.thunderdog.challegram.widget;

import android.content.ClipDescription;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.ResultReceiver;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputConnectionWrapper;

import androidx.core.view.inputmethod.EditorInfoCompat;
import androidx.core.view.inputmethod.InputConnectionCompat;

import org.thunderdog.challegram.Log;

import me.vkryl.core.StringUtils;

public class InputWrapperWrapper {
  private static final String CONTENT_MIME_TYPES = "android.support.v13.view.inputmethod.EditorInfoCompat.CONTENT_MIME_TYPES";

  private static final String COMMIT_CONTENT_ACTION =
    "android.support.v13.view.inputmethod.InputConnectionCompat.COMMIT_CONTENT";
  private static final String COMMIT_CONTENT_CONTENT_URI_KEY =
    "android.support.v13.view.inputmethod.InputConnectionCompat.CONTENT_URI";
  private static final String COMMIT_CONTENT_DESCRIPTION_KEY =
    "android.support.v13.view.inputmethod.InputConnectionCompat.CONTENT_DESCRIPTION";
  private static final String COMMIT_CONTENT_LINK_URI_KEY =
    "android.support.v13.view.inputmethod.InputConnectionCompat.CONTENT_LINK_URI";
  private static final String COMMIT_CONTENT_OPTS_KEY =
    "android.support.v13.view.inputmethod.InputConnectionCompat.CONTENT_OPTS";
  private static final String COMMIT_CONTENT_FLAGS_KEY =
    "android.support.v13.view.inputmethod.InputConnectionCompat.CONTENT_FLAGS";
  private static final String COMMIT_CONTENT_RESULT_RECEIVER =
    "android.support.v13.view.inputmethod.InputConnectionCompat.CONTENT_RESULT_RECEIVER";

  private static final String NEW_COMMIT_CONTENT_ACTION =
    "androidx.core.view.inputmethod.InputConnectionCompat.COMMIT_CONTENT";
  private static final String NEW_COMMIT_CONTENT_CONTENT_URI_KEY =
    "androidx.core.view.inputmethod.InputConnectionCompat.CONTENT_URI";
  private static final String NEW_COMMIT_CONTENT_DESCRIPTION_KEY =
    "androidx.core.view.inputmethod.InputConnectionCompat.CONTENT_DESCRIPTION";
  private static final String NEW_COMMIT_CONTENT_LINK_URI_KEY =
    "androidx.core.view.inputmethod.InputConnectionCompat.CONTENT_LINK_URI";
  private static final String NEW_COMMIT_CONTENT_OPTS_KEY =
    "androidx.core.view.inputmethod.InputConnectionCompat.CONTENT_OPTS";
  private static final String NEW_COMMIT_CONTENT_FLAGS_KEY =
    "androidx.core.view.inputmethod.InputConnectionCompat.CONTENT_FLAGS";
  private static final String NEW_COMMIT_CONTENT_RESULT_RECEIVER =
    "androidx.core.view.inputmethod.InputConnectionCompat.CONTENT_RESULT_RECEIVER";

  public static void setContentMimeTypes (EditorInfo editorInfo, String[] mimeTypes) {
    EditorInfoCompat.setContentMimeTypes(editorInfo, mimeTypes);

  }

  public static InputConnection createWrapper (InputConnection ic, EditorInfo editorInfo, InputConnectionCompat.OnCommitContentListener callback) {
    return InputConnectionCompat.createWrapper(ic, editorInfo, callback);
  }
}
// endregion
