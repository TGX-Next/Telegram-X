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
 * File created on 09/01/2017
 */
// region @MadeByLLM("gpt-6", id = "android13-arm64")
package org.thunderdog.challegram.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.os.Build;
import android.view.View;

import androidx.annotation.NonNull;

import org.thunderdog.challegram.config.Config;
import org.thunderdog.challegram.tool.Views;

import me.vkryl.android.widget.FrameLayoutFix;

public class RectFrameLayout extends FrameLayoutFix {
  private int top, bottom;

  public RectFrameLayout (@NonNull Context context) {
    super(context);

    if ((!Config.DEBUG_CLIPPING)) {
      setOutlineProvider(new android.view.ViewOutlineProvider() {
        @Override

        public void getOutline (View view, android.graphics.Outline outline) {
          outline.setRect(0, top, view.getMeasuredWidth(), view.getMeasuredHeight() - bottom);
        }
      });
      setClipToOutline(true);
    }
  }

  @Override
  protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    checkOutline();
  }

  private void checkOutline () {
    if ((!Config.DEBUG_CLIPPING)) {
      invalidateOutline();
    }
  }

  public void setClip (int top, int bottom) {
    if (this.top != top || this.bottom != bottom) {
      this.top = top;
      this.bottom = bottom;
      checkOutline();
    }
  }

  @Override
  protected void dispatchDraw(Canvas c) {
    if ((!Config.DEBUG_CLIPPING)) {
      super.dispatchDraw(c);
    } else {
      final int saveCount = Views.save(c);
      c.clipRect(0, top, getMeasuredWidth(), getMeasuredHeight() - bottom);
      super.dispatchDraw(c);
      Views.restore(c, saveCount);
    }
  }
}
// endregion
