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
 */
package org.thunderdog.challegram.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.os.Bundle;
import android.os.Parcelable;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.ForumTopicEditorState;
import org.thunderdog.challegram.data.ForumTopicPolicy;
import org.thunderdog.challegram.loader.ComplexReceiver;
import org.thunderdog.challegram.navigation.BackHeaderButton;
import org.thunderdog.challegram.navigation.HeaderView;
import org.thunderdog.challegram.navigation.HeaderButton;
import org.thunderdog.challegram.widget.MaterialEditTextGroup;
import org.thunderdog.challegram.navigation.Menu;
import org.thunderdog.challegram.navigation.NavigationController;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.support.ViewSupport;
import org.thunderdog.challegram.telegram.ChatListener;
import org.thunderdog.challegram.telegram.DateChangeListener;
import org.thunderdog.challegram.telegram.ForumTopicStore;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibCache;
import org.thunderdog.challegram.telegram.TdlibForumTopicManager;
import org.thunderdog.challegram.telegram.TdlibUi;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.ColorState;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.DrawAlgorithms;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import me.vkryl.core.ColorUtils;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.util.text.Text;
import org.thunderdog.challegram.util.text.TextColorSetThemed;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;


/** One fullscreen form for every Create/Edit entry point. Mutations belong to ForumTopicActions. */
@me.vkryl.annotation.MadeByLLM(
  value = "gpt-6.1-sol", others = {"gpt-6-astra", "GPT-6"}, id = "forum-native-screens", ref = "PR #1110"
)
public final class ForumTopicEditController
  extends ViewController<ForumTopicEditController.Arguments>
  implements Menu, ChatListener, TdlibCache.SupergroupDataChangeListener,
    TdlibCache.MyUserDataChangeListener, DateChangeListener {
  private static final long PICKER_TIMEOUT_MS = 12000;

  public static final class Arguments {
    public final long chatId;
    public final int topicId;

    public Arguments (long chatId, int topicId) {
      if (chatId == 0 || topicId < 0) {
        throw new IllegalArgumentException("Invalid topic editor context");
      }
      this.chatId = chatId;
      this.topicId = topicId;
    }
  }

  /** topicId == 0 creates; a positive topicId edits only that topic. */
  public static void open (ViewController<?> owner, long chatId, int topicId) {
    if (owner.isDestroyed()) {
      return;
    }
    ForumTopicEditController controller =
      new ForumTopicEditController(owner.context(), owner.tdlib());
    controller.setArguments(new Arguments(chatId, topicId));
    if (topicId == 0) {
      controller.originTabsHost = ForumTabsNavigation.findHost(owner, chatId);
      controller.returnToTabs = controller.originTabsHost != null;
    }
    if (!owner.navigateTo(controller)) {
      controller.destroy();
    }
  }

  public ForumTopicEditController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  private ForumTopicEditorState form;
  private TdApi.ForumTopicInfo currentInfo;
  private ForumTopicStore.TopicSubscription topicSubscription;
  private ForumTopicStore.ListSession reconcileSession;
  private long supergroupId;
  private boolean subscribed, metadataFailed, metadataMissing, binding, defaultsLoaded,
    pickerLoading;
  private boolean nameTouched, checking, finishing;
  private boolean returnToTabs, finishScheduled;
  private MessagesController originTabsHost;
  private int pickerGeneration, catalogGeneration, pendingCatalog, mutationGeneration;
  private String pickerError = "", selectionError = "", reconcileText = "";
  private TdApi.Sticker[] defaultIcons = new TdApi.Sticker[0];
  private final List<TdApi.Sticker> visibleIcons = new ArrayList<>();
  private final List<IconCell> cells = new ArrayList<>();
  private final List<TextView> colorButtons = new ArrayList<>();
  private final List<TdApi.ForumTopicInfo> candidates = new ArrayList<>();
  private RecyclerView recycler;
  private GridLayoutManager layoutManager;
  private EditorAdapter adapter;
  private boolean rowsRefreshPending;
  private ViewTreeObserver rowsRefreshObserver;
  private final ViewTreeObserver.OnPreDrawListener rowsRefreshListener = () -> {
    applyRowsRefresh();
    return true;
  };
  private final View.OnAttachStateChangeListener rowsRefreshAttachListener =
    new View.OnAttachStateChangeListener() {
      @Override public void onViewAttachedToWindow (View view) {
        applyRowsRefresh();
      }

      @Override public void onViewDetachedFromWindow (View view) {
        clearRowsRefreshListener();
      }
    };
  private LinearLayout content, pickerHeader, colors, results;
  private HorizontalScrollView colorStrip;
  private EditText nameInput;
  private MaterialEditTextGroup nameField;
  private TextView iconHeading;
  private HeaderButton action;
  private TextView nameError, error, hint, pickerStatus, retryPicker, checkResult,
    retryTopic;
  private IconCell preview;
  private Parcelable restoredScroll;
  private int restoredFocus, restoredSelection = -1;
  private boolean initialFocusApplied;

  @Override public int getId () {
    return R.id.controller_forumTopicEdit;
  }

  @Override public long getChatId () {
    return getArgumentsStrict().chatId;
  }

  @Override protected int getBackButton () {
    return BackHeaderButton.TYPE_BACK;
  }

  @Override protected int getMenuId () {
    return R.id.menu_forumTopicEdit;
  }
  // The Save view and listener belong to this editor. Reusing a cached menu skips
  // fillMenuItems(), leaving action null and the listener bound to the old editor.
  @Override protected boolean allowMenuReuse () {
    return false;
  }

  @Override public int getRootColorId () {
    return ColorId.background;
  }

  @Override public CharSequence getName () {
    return Lang.getString(
      getArgumentsStrict().topicId == 0 ? R.string.ForumEditorNew :
        R.string.ForumEditorEdit
    );
  }

  @Override public void setArguments (Arguments args) {
    super.setArguments(args);
    if (args.topicId == 0) {
      form = ForumTopicEditorState.create();
    }
  }

  @Override public void fillMenuItems (int id, HeaderView header, LinearLayout menu) {
    if (id != R.id.menu_forumTopicEdit) {
      return;
    }
    action = header.addDoneButton(menu, this);
    action.setId(R.id.btn_forumTopicSave);
    action.setOnClickListener(v -> submit());
    updateAction();
  }

  @Override public void onMenuItemPressed (int id, View view) {
    if (id == R.id.btn_forumTopicSave) {
      submit();
    }
  }

  @Override protected View onCreateView (Context context) {
    recycler = new RecyclerView(context);
    recycler.setLayoutDirection(
      Lang.rtl() ? View.LAYOUT_DIRECTION_RTL :
        View.LAYOUT_DIRECTION_LTR
    );
    ViewSupport.setThemedBackground(recycler, ColorId.filling, this);
    recycler.setClipToPadding(false);
    recycler.setPadding(Screen.dp(16), 0, Screen.dp(16), extraBottomInset);
    recycler.setItemAnimator(null);
    recycler.setFocusableInTouchMode(true);
    recycler.requestFocus();
    layoutManager = new GridLayoutManager(context, 6);
    layoutManager.setSpanSizeLookup(
      new GridLayoutManager.SpanSizeLookup() {
        @Override public int getSpanSize (int position) {
          return position == 0 || adapter != null && adapter.isHint(position) ?
            layoutManager.getSpanCount() : 1;
        }
      }
    );
    recycler.setLayoutManager(layoutManager);
    createForm();
    adapter = new EditorAdapter();
    recycler.setAdapter(adapter);
    recycler.addOnAttachStateChangeListener(rowsRefreshAttachListener);
    recycler.addOnLayoutChangeListener(
      (v, l, t, r, b, ol, ot, or, ob) -> {
        int spans =
          Math.max(
            3,
            (r - l - recycler.getPaddingLeft() - recycler.getPaddingRight()) / Screen.dp(52)
          );
        if (spans != layoutManager.getSpanCount()) {
          layoutManager.setSpanCount(spans);
        }
      }
    );
    tdlib.listeners().subscribeToChatUpdates(getChatId(), this);
    tdlib.cache().addMyUserListener(this);
    tdlib.context().dateManager().addListener(this);
    TdApi.Chat chat = tdlib.chat(getChatId());
    if (chat != null && chat.type instanceof TdApi.ChatTypeSupergroup) {
      supergroupId = ((TdApi.ChatTypeSupergroup) chat.type).supergroupId;
      tdlib.cache().subscribeToSupergroupUpdates(supergroupId, this);
    }
    subscribed = true;
    if (getArgumentsStrict().topicId != 0) {
      topicSubscription = tdlib.topics()
        .observeTopic(
          key(),
          (topic, failure) -> {
          if (isDestroyed()) {
            return;
          }
          metadataFailed = failure != null;
          metadataMissing = failure != null && (failure.code == 400 || failure.code == 404);
          currentInfo = topic != null &&
            topic.info.chatId == getChatId() &&
            topic.info.forumTopicId == getArgumentsStrict().topicId ? topic.info : null;
          if (currentInfo != null && (form == null || form.editable() && !form.hasChanges())) {
            form = ForumTopicEditorState.edit(
              currentInfo.name,
              currentInfo.icon.color,
              currentInfo.icon.customEmojiId,
              currentInfo.isGeneral
            );
          }
          updateForm();
        }
        );
    }
    loadCatalog();
    updateForm();
    if (restoredScroll != null) {
      layoutManager.onRestoreInstanceState(restoredScroll);
    }
    return recycler;
  }

  private TextView label (float size, int color) {
    TextView view = new TextView(context());
    view.setTextSize(size);
    view.setTypeface(Fonts.getRobotoRegular());
    view.setTextColor(Theme.getColor(color));
    view.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
    addThemeTextColorListener(view, color);
    return view;
  }

  private TextView button (CharSequence title, Runnable click) {
    TextView view = label(14, ColorId.textLink);
    view.setText(title);
    view.setTypeface(Fonts.getRobotoMedium());
    view.setGravity(Gravity.CENTER);
    view.setPadding(Screen.dp(12), Screen.dp(8), Screen.dp(12), Screen.dp(8));
    view.setMinimumHeight(Screen.dp(48));
    view.setFocusable(true);
    view.setBackground(Theme.fillingSelector());
    addThemeInvalidateListener(view);
    view.setOnClickListener(v -> click.run());
    return view;
  }

  private LinearLayout column () {
    LinearLayout view = new LinearLayout(context());
    view.setOrientation(LinearLayout.VERTICAL);
    view.setLayoutDirection(Lang.rtl() ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR);
    return view;
  }

  @me.vkryl.annotation.MadeByLLM(value = "gpt-6.1-sol", id = "forum-editor-mockup")
  private void createForm () {
    content = column();
    content.setLayoutParams(
      new RecyclerView.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
      )
    );
    RecyclerView.LayoutParams contentParams = (RecyclerView.LayoutParams) content.getLayoutParams();
    contentParams.leftMargin = contentParams.rightMargin = -Screen.dp(16);
    nameField = new MaterialEditTextGroup(context(), tdlib);
    nameField.setHint(R.string.ForumEditorNameHint);
    nameField.applyRtl(Lang.rtl());
    nameField.addThemeListeners(this);
    nameInput = nameField.getEditText();
    nameInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
    nameInput.setTextSize(20);
    nameInput.setSaveEnabled(false);
    setLockFocusView(nameInput, false);
    preview = new IconCell(context(), 40);
    preview.setBackground(Theme.transparentSelector());
    preview.setContentDescription(Lang.getString(R.string.ForumEditorPreview));
    preview.setOnClickListener(v -> {
      if (form != null && !form.general && form.editable()) {
        hideSoftwareKeyboard();
        recycler.smoothScrollToPosition(1);
      }
    });
    LinearLayout card = column();
    ViewSupport.setThemedBackground(card, ColorId.filling, this);
    LinearLayout nameRow = new LinearLayout(context());
    nameRow.setGravity(Gravity.CENTER_VERTICAL);
    nameRow.setLayoutDirection(Lang.rtl() ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR);
    nameRow.setPadding(Screen.dp(20), Screen.dp(12), Screen.dp(20), Screen.dp(12));
    nameRow.addView(preview, new LinearLayout.LayoutParams(Screen.dp(56), Screen.dp(56)));
    LinearLayout.LayoutParams fieldParams = new LinearLayout.LayoutParams(0, Screen.dp(76), 1f);
    fieldParams.setMarginStart(Screen.dp(20));
    nameRow.addView(nameField, fieldParams);
    card.addView(nameRow, new LinearLayout.LayoutParams(-1, Screen.dp(104)));
    nameInput.setImeOptions(EditorInfo.IME_ACTION_DONE);
    nameInput.setOnEditorActionListener(
      (v, id, event) -> {
        if (id != EditorInfo.IME_ACTION_DONE) {
          return false;
        }
        submit();
        return true;
      }
    );
    nameError = label(13, ColorId.textNegative);
    nameError.setPadding(Screen.dp(20), 0, Screen.dp(20), Screen.dp(8));
    nameError.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
    card.addView(nameError);
    colors = new LinearLayout(context());
    int[] colorLabels =
      {
        R.string.ForumColorBlue,
        R.string.ForumColorYellow,
        R.string.ForumColorPurple,
        R.string.ForumColorGreen,
        R.string.ForumColorPink,
        R.string.ForumColorRed
      };
    for (int i = 0; i < ForumTopicPolicy.ICON_COLORS.length; i++) {
      final int color = ForumTopicPolicy.ICON_COLORS[i];
      TextView choice = button(
        "●",
        () -> {
          if (form != null && form.setColor(color)) {
            updateForm();
          }
        }
      );
      choice.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 26);
      choice.setPadding(0, 0, 0, 0);
      choice.setTextColor(0xff000000 | color);
      // These six RGBs are TDLib's topic colors, not theme colors.
      removeThemeListenerByTarget(choice);
      choice.setContentDescription(Lang.getString(colorLabels[i]));
      colorButtons.add(choice);
      colors.addView(choice, new LinearLayout.LayoutParams(Screen.dp(48), Screen.dp(48)));
    }
    colorStrip = new HorizontalScrollView(context());
    colorStrip.setHorizontalScrollBarEnabled(false);
    colorStrip.addView(colors);
    card.addView(colorStrip);
    content.addView(card);
    hint = label(13, ColorId.textLight);
    hint.setPadding(Screen.dp(16), Screen.dp(12), Screen.dp(16), Screen.dp(12));
    content.addView(hint);
    error = label(14, ColorId.textNegative);
    error.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
    error.setPadding(Screen.dp(16), Screen.dp(8), Screen.dp(16), Screen.dp(8));
    content.addView(error);
    retryTopic = button(
      Lang.getString(R.string.ForumEditorRetry),
      () -> {
        metadataMissing = false;
        metadataFailed = false;
        tdlib.topics().retryTopic(key());
        updateForm();
      }
    );
    content.addView(retryTopic);
    checkResult = button(Lang.getString(R.string.ForumEditorCheckResult), this::reconcile);
    content.addView(checkResult);
    results = column();
    content.addView(results);
    pickerHeader = column();
    ViewSupport.setThemedBackground(pickerHeader, ColorId.filling, this);
    View separator = new View(context());
    ViewSupport.setThemedBackground(separator, ColorId.background, this);
    pickerHeader.addView(separator, new LinearLayout.LayoutParams(-1, Screen.dp(12)));
    LinearLayout iconTitle = new LinearLayout(context());
    iconTitle.setGravity(Gravity.CENTER_VERTICAL);
    iconTitle.setPaddingRelative(Screen.dp(20), 0, Screen.dp(8), 0);
    iconHeading = label(15, ColorId.textLink);
    iconHeading.setTypeface(Fonts.getRobotoMedium());
    iconHeading.setText(R.string.ForumEditorIconHeading);
    iconTitle.addView(iconHeading, new LinearLayout.LayoutParams(0, -2, 1f));
    HeaderButton info = new HeaderButton(context());
    info.setImageResource(R.drawable.baseline_info_24);
    info.setThemeColorId(ColorId.iconLight);
    info.setContentDescription(Lang.getString(R.string.ForumEditorIconHelp));
    info.setOnClickListener(v -> showIconHelp());
    info.setBackground(Theme.transparentSelector());
    addThemeInvalidateListener(info);
    iconTitle.addView(info, new LinearLayout.LayoutParams(Screen.dp(48), Screen.dp(48)));
    pickerHeader.addView(iconTitle, new LinearLayout.LayoutParams(-1, Screen.dp(48)));
    pickerStatus = label(14, ColorId.textLight);
    pickerStatus.setGravity(Gravity.CENTER);
    pickerStatus.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
    pickerHeader.addView(pickerStatus);
    retryPicker = button(Lang.getString(R.string.ForumEditorRetry), this::loadCatalog);
    pickerHeader.addView(retryPicker);
    content.addView(pickerHeader);
    nameInput.addTextChangedListener(
      watcher(
        () -> {
          if (!binding && form != null && form.setName(nameInput.getText().toString())) {
            nameTouched = true;
            updateForm();
          }
        }
      )
    );
  }

  private static TextWatcher watcher (Runnable changed) {
    return new TextWatcher() {
      @Override public void beforeTextChanged (CharSequence s, int start, int count, int after) {
      }

      @Override public void onTextChanged (CharSequence s, int start, int before, int count) {
      }

      @Override public void afterTextChanged (Editable s) {
        changed.run();
      }
    };
  }

  private TdlibForumTopicManager.Key key () {
    return new TdlibForumTopicManager.Key(getChatId(), getArgumentsStrict().topicId);
  }

  private boolean available () {
    TdApi.Chat chat = tdlib.chat(getChatId());
    if (chat == null || !tdlib.isForum(chat.id)) {
      return false;
    }
    return getArgumentsStrict().topicId == 0 ?
      ForumTopicPolicy.canCreate(tdlib.chatStatus(chat.id), chat.permissions) :
      !metadataFailed &&
        currentInfo != null &&
        ForumTopicPolicy.canEdit(tdlib.chatStatus(chat.id), currentInfo);
  }

  private boolean iconAllowed () {
    return form != null &&
      ForumTopicPolicy.allowedIcon(form.emoji(), false, defaultIcons);
  }

  private void updateAction () {
    if (action == null) {
      return;
    }
    boolean pending = form != null && form.phase() == ForumTopicEditorState.Phase.PENDING;
    action.setContentDescription(
      Lang.getString(
        pending ? R.string.ForumSaving :
          getArgumentsStrict().topicId == 0 ? R.string.ForumEditorCreate : R.string.Save
      )
    );
    action.setEnabled(form != null && form.canSubmit(available(), iconAllowed(), defaultsLoaded));
    action.setAlpha(action.isEnabled() || pending ? 1f : .45f);
    action.setShowProgress(pending, -1f);
  }

  private void updateForm () {
    updateAction();
    if (nameInput == null) {
      return;
    }
    boolean editable = form != null && form.editable();
    nameField.setInputEnabled(editable);
    hint.setVisibility(form != null && form.general ? View.VISIBLE : View.GONE);
    colorStrip.setVisibility(form != null && form.creating ? View.VISIBLE : View.GONE);
    boolean showPicker = form != null && !form.general;
    if (adapter != null) {
      adapter.setPickerVisible(showPicker);
    }
    pickerHeader.setVisibility(showPicker ? View.VISIBLE : View.GONE);
    retryTopic.setVisibility(metadataFailed && !metadataMissing ? View.VISIBLE : View.GONE);
    boolean unknown = form != null && form.phase() == ForumTopicEditorState.Phase.UNKNOWN;
    checkResult.setVisibility(unknown ? View.VISIBLE : View.GONE);
    checkResult.setEnabled(!checking);
    results.setVisibility(unknown ? View.VISIBLE : View.GONE);
    String message = "";
    if (form == null) {
      message = Lang.getString(
        metadataMissing ? R.string.ForumEditorUnavailable : metadataFailed ?
          R.string.ForumEditorLoadError : R.string.ForumTopicLoading
      );
    } else {
      binding = true;
      if (!nameInput.getText().toString().equals(form.name())) {
        nameInput.setText(form.name());
      }
      binding = false;
      ForumTopicEditorState.Error validation =
        form.validate(available(), iconAllowed(), defaultsLoaded);
      String nameMessage = validation == ForumTopicEditorState.Error.EMPTY_NAME &&
        nameTouched ? Lang.getString(R.string.ForumEditorEmptyName) :
        validation == ForumTopicEditorState.Error.LONG_NAME ?
          Lang.getString(R.string.ForumEditorLongName) : "";
      nameError.setText(nameMessage);
      nameError.setVisibility(nameMessage.isEmpty() ? View.GONE : View.VISIBLE);
      if (unknown) {
        message = Lang.getString(R.string.ForumEditorUnknown);
      } else if (form.phase() == ForumTopicEditorState.Phase.PENDING) {
        message = Lang.getString(R.string.ForumSaving);
      } else if (!available()) {
        message = Lang.getString(
          !form.creating &&
            currentInfo == null &&
            !metadataFailed ? R.string.ForumTopicLoading :
            metadataFailed &&
              !metadataMissing ? R.string.ForumEditorLoadError : form.creating ?
                R.string.ForumEditorCreateUnavailable : R.string.ForumEditorUnavailable
        );
      } else if (!form.serverError().isEmpty()) {
        message = form.serverError();
      } else if (!selectionError.isEmpty()) {
        message = selectionError;
      } else if (validation == ForumTopicEditorState.Error.PREMIUM) {
        message = Lang.getString(R.string.ForumEditorSelectIcon);
      } else if (validation == ForumTopicEditorState.Error.ICON_LOADING) {
        message = Lang.getString(R.string.ForumEditorIconLoading);
      } else if (validation == ForumTopicEditorState.Error.INVALID_COLOR) {
        message = Lang.getString(R.string.ForumEditorColorInvalid);
      }
      hint.setText(Lang.getString(R.string.ForumEditorGeneralHint));
      preview.bind(
        form.emoji(),
        form.general ? "#" :
          form.submittedName(),
        false,
        false,
        Lang.getString(R.string.ForumEditorPreview)
      );
      preview.setEnabled(editable && !form.general);
      for (int i = 0; i < colorButtons.size(); i++) {
        TextView choice = colorButtons.get(i);
        choice.setEnabled(editable);
        choice.setSelected(form.color() == ForumTopicPolicy.ICON_COLORS[i]);
        choice.setText(choice.isSelected() ? "◉" : "●");
      }
    }
    error.setText(message);
    error.setVisibility(message.isEmpty() ? View.GONE : View.VISIBLE);
    refreshIconRows();
    renderPickerStatus();
    restoreFocusIfReady();
  }

  private void refreshIconRows () {
    if (isDestroyed() || adapter == null || form == null) {
      return;
    }
    rowsRefreshPending = true;
    applyRowsRefresh();
  }

  private void applyRowsRefresh () {
    if (isDestroyed() || !rowsRefreshPending || adapter == null || form == null ||
      recycler == null || recycler.getWindowToken() == null) {
      return;
    }
    // updateRows uses layout positions: wait until structural updates have been consumed.
    if (recycler.isComputingLayout() || recycler.hasPendingAdapterUpdates() ||
      recycler.isLayoutRequested()) {
      if (rowsRefreshObserver == null || !rowsRefreshObserver.isAlive()) {
        rowsRefreshObserver = recycler.getViewTreeObserver();
        rowsRefreshObserver.addOnPreDrawListener(rowsRefreshListener);
      }
      return;
    }
    clearRowsRefreshListener();
    rowsRefreshPending = false;
    // Row 0 owns the form and its focused inputs; only icon rows need rebinding.
    Views.updateRows(recycler, 1, adapter.getItemCount(), this::bindVisibleRow);
  }

  private void clearRowsRefreshListener () {
    if (rowsRefreshObserver != null) {
      if (rowsRefreshObserver.isAlive()) {
        rowsRefreshObserver.removeOnPreDrawListener(rowsRefreshListener);
      } else if (recycler != null) {
        recycler.getViewTreeObserver().removeOnPreDrawListener(rowsRefreshListener);
      }
      rowsRefreshObserver = null;
    }
  }

  private void bindVisibleRow (View view) {
    int position = recycler.getChildAdapterPosition(view);
    if (view instanceof IconCell && position >= 1 && position < adapter.getItemCount() &&
      recycler.getChildViewHolder(view).getItemId() == adapter.getItemId(position)) {
      adapter.bindIcon((IconCell) view, position);
    } else if (view instanceof TextView && adapter.isHint(position)) {
      adapter.bindHint((TextView) view);
    }
  }

  private void submit () {
    nameTouched = true;
    selectionError = "";
    if (form == null || !form.beginSubmit(available(), iconAllowed(), defaultsLoaded)) {
      updateForm();
      return;
    }
    hideSoftwareKeyboard();
    updateForm();
    final ForumTopicEditorState submitted = form;
    final int mutation = ++mutationGeneration;
    // Store timeouts normally finish first. Its account-reset guard may discard a callback,
    // so the view also bounds waiting without ever repeating the mutation.
    tdlib.ui()
      .postDelayed(
        () -> {
        if (isDestroyed() ||
          mutation != mutationGeneration ||
          form != submitted ||
          form.phase() != ForumTopicEditorState.Phase.PENDING) {
          return;
        }
        form.failed(408, Lang.getString(R.string.ForumEditorServerError));
        updateForm();
        if (form.phase() == ForumTopicEditorState.Phase.UNKNOWN) {
          reconcile();
        }
      },
        18000
      );
    if (form.creating) {
      tdlib.topics().actions.create(
        getChatId(),
        form.submittedName(),
        false,
        new TdApi.ForumTopicIcon(form.color(), form.emoji()),
        (value, failure) -> {
        if (isDestroyed() || mutation != mutationGeneration || submitted != form) {
          return;
        }
        if (failure != null) {
          form.failed(failure.code, failureText(failure));
        } else if (value != null && value.chatId == getChatId() && value.forumTopicId > 0) {
          form.succeeded(value.forumTopicId);
        } else {
          form.failed(502, Lang.getString(R.string.ForumEditorServerError));
        }
        updateForm();
        if (form.phase() == ForumTopicEditorState.Phase.UNKNOWN) {
          reconcile();
        }
        finishIfReady();
      }
      );
    } else {
      tdlib.topics().actions.edit(
        key(),
        form.submittedName(),
        form.changesEmoji(),
        form.emoji(),
        (value, failure) -> {
        if (isDestroyed() || mutation != mutationGeneration || submitted != form) {
          return;
        }
        if (failure != null) {
          form.failed(failure.code, failureText(failure));
        } else {
          form.succeeded(getArgumentsStrict().topicId);
        }
        updateForm();
        finishIfReady();
      }
      );
    }
  }

  private String failureText (TdApi.Error failure) {
    // Avoid rendering raw protocol strings; errors are localized and never contain diagnostic data.
    return Lang.getString(
      failure.code == 400 ||
        failure.code == 403 ? R.string.ForumEditorPermissionError : R.string.ForumEditorServerError
    );
  }

  private void finishIfReady () {
    if (isDestroyed() ||
      !isFocused() ||
      finishing ||
      form == null ||
      form.phase() != ForumTopicEditorState.Phase.SUCCEEDED) {
      return;
    }
    final NavigationController navigation = context().navigation();
    if (navigation == null || navigation.getCurrentStackItem() != this) {
      return;
    }
    if (context().isNavigationBusy() || navigation.getStack().isLocked()) {
      scheduleFinish();
      return;
    }
    if (form.creating && returnToTabs && ForumTabsNavigation.isTabForum(this, getChatId())) {
      if (!ForumTabsNavigation.isLiveHost(this, getChatId(), originTabsHost)) {
        originTabsHost = ForumTabsNavigation.findHost(this, getChatId());
      }
      if (originTabsHost != null) {
        finishing = true;
        ForumTabsNavigation.Result result =
          ForumTabsNavigation.requestAndReturn(this, originTabsHost, form.completedTopicId());
        if (result == ForumTabsNavigation.Result.RETURNED ||
          result == ForumTabsNavigation.Result.QUEUED &&
            navigateBack()) {
          form.claimCompletion();
          return;
        }
        finishing = false;
        if (result == ForumTabsNavigation.Result.RETRY ||
          result == ForumTabsNavigation.Result.QUEUED) {
          scheduleFinish();
        }
        // A live host rejecting a request must not create a duplicate host. A
        // later focus can retry navigation, but never repeat the Create mutation.
        return;
      }
    }
    if (!form.claimCompletion()) {
      return;
    }
    finishing = true;
    if (!form.creating) {
      navigateBack();
      return;
    }
    tdlib.ui()
      .openChat(
        this,
        getChatId(),
        new TdlibUi.ChatOpenParameters()
          .messageTopic(new TdApi.MessageTopicForum(form.completedTopicId()))
          .keepStack()
          .after(chatId -> tdlib.ui().post(() -> removeCompletedEditor(navigation)))
      );
  }

  private void scheduleFinish () {
    if (finishScheduled) {
      return;
    }
    finishScheduled = true;
    tdlib.ui()
      .postDelayed(
        () -> {
        finishScheduled = false;
        finishIfReady();
      },
        120
      );
  }

  boolean canReturnToForumTabs () {
    return ForumTabsNavigation.canLeaveEditor(form);
  }

  @me.vkryl.annotation.MadeByLLM(value = "gpt-6-astra", id = "forum-topics")
  public boolean completeCreationNavigation (MessagesController controller) {
    if (isDestroyed() ||
      controller.isDestroyed() ||
      controller.tdlib() != tdlib ||
      controller.getChatId() != getChatId() ||
      form == null ||
      !form.creating ||
      form.phase() != ForumTopicEditorState.Phase.SUCCEEDED ||
      form.completedTopicId() <= 0) {
      return false;
    }
    TdApi.MessageTopic topic = controller.getMessageTopicId();
    if (!(topic instanceof TdApi.MessageTopicForum) ||
      ((TdApi.MessageTopicForum) topic).forumTopicId != form.completedTopicId()) {
      return false;
    }
    // Focus can precede the deferred editor cleanup, including after stack restore.
    // Keep the outgoing view until the navigation processor has finished with it.
    tdlib.ui().post(() -> removeCompletedEditor(context().navigation()));
    return true;
  }

  private void removeCompletedEditor (NavigationController navigation) {
    if (isDestroyed() || navigation == null) {
      return;
    }
    // ChatOpenParameters.after runs at animation readiness, not at completion. The
    // navigation processor still needs the editor as getPrevious() to detach its
    // view. Removing it earlier strands the editor above every subsequent page.
    // region @MadeByLLM("gpt-6-astra", id = "forum-topics")
    if (navigation.isAnimating() || navigation.getStack().isLocked()) {
    // endregion
      tdlib.ui().postDelayed(() -> removeCompletedEditor(navigation), 100);
      return;
    }
    final int index = navigation.getStack().indexOf(this);
    if (index >= 0 && index < navigation.getStackSize() - 1) {
      if (getValue().getParent() != null) {
        navigation.removeChildWrapper(this);
      }
      navigation.getStack().destroy(index);
    }
  }

  private void reconcile () {
    if (isDestroyed() ||
      checking ||
      form == null ||
      form.phase() != ForumTopicEditorState.Phase.UNKNOWN) {
      return;
    }
    if (reconcileSession != null) {
      reconcileSession.close();
    }
    checking = true;
    candidates.clear();
    reconcileText = Lang.getString(R.string.ForumEditorChecking);
    renderResults();
    updateForm();
    reconcileSession = tdlib.topics()
      .openList(
        getChatId(),
        form.submittedName(),
        snapshot -> {
        if (isDestroyed() || form.phase() != ForumTopicEditorState.Phase.UNKNOWN) {
          return;
        }
        if (snapshot.error != null) {
          checking = false;
          reconcileText = Lang.getString(R.string.ForumEditorCheckFailed);
        } else if (snapshot.initialized &&
          !snapshot.stale &&
          !snapshot.refreshing &&
          !snapshot.loadingInitial) {
          candidates.clear();
          for (TdApi.ForumTopic topic : snapshot.topics) {
            TdApi.ForumTopicInfo info = topic.info;
            if (info.chatId == getChatId() &&
              form.matchesCandidate(
                info.name,
                info.icon.color,
                info.icon.customEmojiId,
                info.isOutgoing
              )) {
              candidates.add(info);
            }
          }
          if (!snapshot.endReached && snapshot.topics.size() < 200 && reconcileSession != null) {
            reconcileSession.loadMore();
            return;
          }
          checking = false;
          reconcileText =
            Lang.getString(
              candidates.isEmpty() ? R.string.ForumEditorNoMatch :
                R.string.ForumEditorMatches
            );
        }
        renderResults();
        updateForm();
      }
      );
    reconcileSession.refresh();
  }

  private void renderResults () {
    for (int i = 0; i < results.getChildCount(); i++) {
      removeThemeListenerByTarget(results.getChildAt(i));
    }
    results.removeAllViews();
    TextView text = label(14, ColorId.textLight);
    text.setText(reconcileText);
    results.addView(text);
    for (int i = 0; i < candidates.size(); i++) {
      TdApi.ForumTopicInfo candidate = candidates.get(i);
      final int topicId = candidate.forumTopicId;
      TextView button = button(
        candidateText(candidate, i),
        () -> {
          if (form.resolveToExisting(topicId)) {
            finishIfReady();
          }
        }
      );
      button.setTag(candidate);
      results.addView(button);
    }
  }

  private String candidateText (TdApi.ForumTopicInfo candidate, int index) {
    String date = Lang.getString(
      R.string.format_dateTime,
      Lang.getDate(candidate.creationDate, TimeUnit.SECONDS),
      Lang.time(candidate.creationDate, TimeUnit.SECONDS)
    );
    return Lang.getString(
      R.string.format_nameAndDate,
      Lang.getString(R.string.ForumEditorOpenMatch, index + 1),
      date
    );
  }

  private void refreshCandidateTexts () {
    if (isDestroyed() || results == null) {
      return;
    }
    // Keep the existing buttons, click targets and focus; never restart reconciliation here.
    for (int i = 1; i < results.getChildCount(); i++) {
      View view = results.getChildAt(i);
      if (view instanceof TextView && view.getTag() instanceof TdApi.ForumTopicInfo) {
        ((TextView) view).setText(candidateText((TdApi.ForumTopicInfo) view.getTag(), i - 1));
      }
    }
  }

  @Override public void onDateChanged () {
    refreshCandidateTexts();
  }

  @Override public void onTimeFormatChanged () {
    // DateManager has already cleared the shared date/locale format caches.
    refreshCandidateTexts();
  }

  @Override public void handleLanguagePackEvent (int event, int arg1) {
    super.handleLanguagePackEvent(event, arg1);
    refreshCandidateTexts();
    if (nameField != null) {
      iconHeading.setText(R.string.ForumEditorIconHeading);
      nameField.setHint(R.string.ForumEditorNameHint);
    }
    refreshIconRows();
  }

  private void loadCatalog () {
    final int generation = ++catalogGeneration;
    pendingCatalog = 1;
    pickerError = "";
    pickerLoading = true;
    tdlib.send(
      new TdApi.GetForumTopicDefaultIcons(),
      (icons, failure) -> tdlib.ui()
        .post(
          () -> {
          if (isDestroyed() || generation != catalogGeneration) {
            return;
          }
          defaultsLoaded = icons != null;
          if (icons != null) {
            defaultIcons = icons.stickers;
          } else {
            pickerError = Lang.getString(R.string.ForumEditorEmojiError);
          }
          pendingCatalog = 0;
          loadPicker();
          updateForm();
        }
        )
    );
    tdlib.ui()
      .postDelayed(
        () -> {
        if (isDestroyed() || generation != catalogGeneration || pendingCatalog == 0) {
          return;
        }
        ++catalogGeneration; // Discard late results from this timed-out catalog load.
        pendingCatalog = 0;
        if (pickerLoading) {
          showIcons(++pickerGeneration, null);
        }
        renderPickerStatus();
      },
        PICKER_TIMEOUT_MS
      );
    renderPickerStatus();
  }

  private void showIconHelp () {
    int text = form != null && form.general ? R.string.ForumEditorGeneralHint :
      R.string.ForumEditorIconsFree;
    String message = Lang.getString(text);
    if (form != null && !form.creating) {
      message = Lang.getString(R.string.ForumEditorEditHint, message,
        Lang.getString(R.string.ForumEditorColorLocked));
    }
    showAlert(new AlertDialog.Builder(context(), Theme.dialogTheme())
      .setTitle(Lang.getString(R.string.ForumEditorIconHeading))
      .setMessage(message).setPositiveButton(Lang.getString(R.string.OK), null));
  }

  private void loadPicker () {
    showIcons(++pickerGeneration, defaultsLoaded ? defaultIcons : null);
  }

  private void showIcons (int ticket, TdApi.Sticker[] stickers) {
    if (isDestroyed() || ticket != pickerGeneration) {
      return;
    }
    pickerLoading = false;
    pickerError = stickers == null ? Lang.getString(R.string.ForumEditorEmojiError) : "";
    LinkedHashMap<Long, TdApi.Sticker> unique = new LinkedHashMap<>();
    if (stickers != null) {
      for (TdApi.Sticker sticker : stickers) {
        if (sticker.fullType instanceof TdApi.StickerFullTypeCustomEmoji) {
          unique.put(((TdApi.StickerFullTypeCustomEmoji) sticker.fullType).customEmojiId, sticker);
        }
      }
    }
    adapter.replaceIcons(new ArrayList<>(unique.values()));
    renderPickerStatus();
  }

  private void renderPickerStatus () {
    if (pickerStatus == null) {
      return;
    }
    String text = pickerLoading ? Lang.getString(R.string.ForumEditorEmojiLoading) :
      !pickerError.isEmpty() ? pickerError :
      visibleIcons.isEmpty() ? Lang.getString(R.string.ForumEditorEmojiEmpty) : "";
    refreshIconRows();
    pickerStatus.setText(text);
    pickerStatus.setVisibility(text.isEmpty() ? View.GONE : View.VISIBLE);
    retryPicker.setVisibility(
      !pickerLoading &&
        !pickerError.isEmpty() ? View.VISIBLE : View.GONE
    );
  }

  private void chooseIcon (long emojiId) {
    if (form == null || !form.editable() || form.general) {
      return;
    }
    if (!ForumTopicPolicy.allowedIcon(emojiId, false, defaultIcons)) {
      selectionError =
        Lang.getString(
          defaultsLoaded ? R.string.ForumEditorEmojiError :
            R.string.ForumEditorIconLoading
        );
      updateForm();
      return;
    }
    if (form.setEmoji(emojiId)) {
      selectionError = "";
      updateForm();
    }
  }

  @Override public void onFocus () {
    super.onFocus();
    updateForm();
    finishIfReady();
    if (form != null &&
      form.phase() == ForumTopicEditorState.Phase.UNKNOWN &&
      reconcileSession == null) {
      reconcile();
    }
  }

  private void restoreFocusIfReady () {
    if (isFocused() && !initialFocusApplied && nameInput != null && nameInput.isEnabled()) {
      initialFocusApplied = true;
      if (restoredFocus == 0) {
        nameInput.requestFocus();
      }
    }
    if (isFocused() && restoredFocus != 0 && nameInput != null) {
      EditText target = nameInput;
      if (!target.isEnabled()) {
        return;
      }
      target.requestFocus();
      if (restoredSelection >= 0) {
        target.setSelection(Math.min(restoredSelection, target.length()));
      }
      restoredFocus = 0;
    }
  }

  @Override public void onChatPermissionsChanged (long chatId, TdApi.ChatPermissions permissions) {
    tdlib.ui().post(this::refreshAccess);
  }

  @Override public void onSupergroupUpdated (TdApi.Supergroup supergroup) {
    tdlib.ui().post(this::refreshAccess);
  }

  @Override public void onMyUserUpdated (TdApi.User user) {
    tdlib.ui().post(this::refreshAccess);
  }

  private void refreshAccess () {
    if (!isDestroyed()) {
      updateForm();
    }
  }

  @Override public boolean supportsBottomInset () {
    return true;
  }

  @Override protected void onBottomInsetChanged (int inset, int withoutIme, boolean isIme) {
    super.onBottomInsetChanged(inset, withoutIme, isIme);
    if (recycler != null) {
      recycler.setPadding(Screen.dp(16), 0, Screen.dp(16), inset);
      if (nameInput != null && nameInput.hasFocus()) {
        nameInput.requestRectangleOnScreen(
          new android.graphics.Rect(
            0,
            0,
            nameInput.getWidth(),
            nameInput.getHeight()
          )
        );
      }
    }
  }

  @Override public boolean performOnBackPressed (boolean fromTop, boolean commit) {
    if (super.performOnBackPressed(fromTop, commit)) {
      return true;
    }
    if (finishing || form == null || !form.hasChanges()) {
      return false;
    }
    if (!commit) {
      return true;
    }
    if (form.phase() == ForumTopicEditorState.Phase.PENDING) {
      selectionError = Lang.getString(R.string.ForumEditorSendingBack);
      updateForm();
      return true;
    }
    boolean unknown = form.phase() == ForumTopicEditorState.Phase.UNKNOWN;
    showAlert(
      new AlertDialog.Builder(context(), Theme.dialogTheme())
        .setTitle(Lang.getString(R.string.ForumEditorDiscardTitle))
        .setMessage(
          Lang.getString(
            unknown ? R.string.ForumEditorUnknownLeave :
              R.string.ForumEditorDiscardText
          )
        )
        .setNegativeButton(Lang.getString(R.string.ForumEditorKeepEditing), null)
        .setPositiveButton(
          Lang.getString(unknown ? R.string.ForumEditorLeave : R.string.Discard),
          (dialog, which) -> {
          finishing = true;
          navigateBack();
        }
        )
    );
    return true;
  }

  @Override public boolean canSlideBackFrom (NavigationController navigation, float x, float y) {
    return form == null || !form.hasChanges();
  }

  @Override public boolean saveInstanceState (Bundle out, String prefix) {
    super.saveInstanceState(out, prefix);
    out.putInt(prefix + "editor_account", tdlib.id());
    out.putLong(prefix + "editor_chat", getChatId());
    out.putInt(prefix + "editor_topic", getArgumentsStrict().topicId);
    out.putBoolean(prefix + "editor_tabs_origin", returnToTabs);
    if (form != null) {
      out.putSerializable(prefix + "editor_form", form.snapshot());
    }
    out.putBoolean(prefix + "editor_touched", nameTouched);
    if (layoutManager != null) {
      out.putParcelable(prefix + "editor_scroll", layoutManager.onSaveInstanceState());
    }
    int focus = nameInput != null && nameInput.hasFocus() ? 1 : 0;
    out.putInt(prefix + "editor_focus", focus);
    if (focus != 0) {
      out.putInt(
        prefix + "editor_selection",
        nameInput.getSelectionStart()
      );
    }
    return true;
  }

  @Override public boolean restoreInstanceState (Bundle in, String prefix) {
    long chatId = in.getLong(prefix + "editor_chat");
    int topicId = in.getInt(prefix + "editor_topic", -1);
    if (chatId == 0 || topicId < 0 || in.getInt(prefix + "editor_account", -1) != tdlib.id()) {
      return false;
    }
    super.restoreInstanceState(in, prefix);
    setArguments(new Arguments(chatId, topicId));
    returnToTabs = topicId == 0 && in.getBoolean(prefix + "editor_tabs_origin", false);
    originTabsHost = null; // Resolve only within the restored account/chat stack.
    Object saved = in.getSerializable(prefix + "editor_form");
    if (saved instanceof ForumTopicEditorState.Snapshot) {
      ForumTopicEditorState.Snapshot snapshot = (ForumTopicEditorState.Snapshot) saved;
      if (snapshot.creating != (topicId == 0)) {
        return false;
      }
      form = ForumTopicEditorState.restore(snapshot);
      finishing = snapshot.completionConsumed;
      if (!snapshot.creating && snapshot.phase == ForumTopicEditorState.Phase.PENDING) {
        selectionError = Lang.getString(R.string.ForumEditorRestoredEdit);
      }
    }
    nameTouched = in.getBoolean(prefix + "editor_touched");
    restoredScroll = in.getParcelable(prefix + "editor_scroll");
    restoredFocus = in.getInt(prefix + "editor_focus") == 1 ? 1 : 0;
    restoredSelection = in.getInt(prefix + "editor_selection", -1);
    return true;
  }

  @Override public void onThemeColorsChanged (boolean temporary, ColorState state) {
    super.onThemeColorsChanged(temporary, state);
    if (recycler != null) {
      recycler.invalidate();
    }
    for (IconCell cell : cells) {
      cell.invalidate();
    }
  }

  @Override public void destroy () {
    rowsRefreshPending = false;
    clearRowsRefreshListener();
    if (recycler != null) {
      recycler.removeOnAttachStateChangeListener(rowsRefreshAttachListener);
    }
    if (topicSubscription != null) {
      topicSubscription.close();
    }
    if (reconcileSession != null) {
      reconcileSession.close();
    }
    if (subscribed) {
      tdlib.listeners().unsubscribeFromChatUpdates(getChatId(), this);
      tdlib.cache().removeMyUserListener(this);
      tdlib.context().dateManager().removeListener(this);
      if (supergroupId != 0) {
        tdlib.cache().unsubscribeFromSupergroupUpdates(supergroupId, this);
      }
      subscribed = false;
    }
    ++pickerGeneration;
    ++catalogGeneration;
    for (IconCell cell : cells) {
      cell.destroyCell();
    }
    cells.clear();
    super.destroy();
  }

  private final class EditorAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private final LinkedHashMap<Long, Long> stableIds = new LinkedHashMap<>();
    private boolean pickerVisible = form != null && !form.general;

    EditorAdapter () {
      setHasStableIds(true);
    }

    @Override public int getItemCount () {
      return pickerVisible ? visibleIcons.size() + 3 : 1;
    }

    void setPickerVisible (boolean visible) {
      if (pickerVisible == visible) {
        return;
      }
      pickerVisible = visible;
      // The form header is always row 0; topic icons and their footer follow it.
      int count = visibleIcons.size() + 2;
      if (visible) {
        notifyItemRangeInserted(1, count);
      } else {
        notifyItemRangeRemoved(1, count);
      }
    }

    void replaceIcons (List<TdApi.Sticker> icons) {
      int removedCount = visibleIcons.size();
      visibleIcons.clear();
      if (pickerVisible && removedCount > 0) {
        notifyItemRangeRemoved(2, removedCount);
      }
      visibleIcons.addAll(icons);
      if (pickerVisible && !visibleIcons.isEmpty()) {
        notifyItemRangeInserted(2, visibleIcons.size());
      }
    }

    @Override public int getItemViewType (int position) {
      return position == 0 ? 0 : isHint(position) ? 2 : 1;
    }

    boolean isHint (int position) {
      return pickerVisible && position == getItemCount() - 1;
    }

    @Override public long getItemId (int position) {
      if (isHint(position)) {
        return Long.MIN_VALUE;
      }
      if (position < 2) {
        return position;
      }
      long emojiId =
        ((TdApi.StickerFullTypeCustomEmoji) visibleIcons.get(position - 2).fullType).customEmojiId;
      // Protocol identifiers are opaque; do not reserve values from their namespace for rows.
      Long stable = stableIds.get(emojiId);
      if (stable == null) {
        stableIds.put(emojiId, stable = stableIds.size() + 2L);
      }
      return stable;
    }

    @NonNull @Override public RecyclerView.ViewHolder onCreateViewHolder (
      @NonNull ViewGroup parent,
      int type
    ) {
      if (type == 0) {
        RecyclerView.ViewHolder holder = new RecyclerView.ViewHolder(content) {
        };
        holder.setIsRecyclable(false);
        return holder;
      }
      if (type == 2) {
        TextView footer = label(12, ColorId.textLight);
        footer.setGravity(Gravity.CENTER);
        footer.setPadding(Screen.dp(12), Screen.dp(12), Screen.dp(12), Screen.dp(20));
        footer.setLayoutParams(new RecyclerView.LayoutParams(-1, -2));
        return new RecyclerView.ViewHolder(footer) {};
      }
      IconCell cell = new IconCell(parent.getContext());
      cell.setLayoutParams(
        new RecyclerView.LayoutParams(
          ViewGroup.LayoutParams.MATCH_PARENT,
          Screen.dp(56)
        )
      );
      return new RecyclerView.ViewHolder(cell) {
      };
    }

    @Override public void onBindViewHolder (@NonNull RecyclerView.ViewHolder holder, int position) {
      if (position == 0) {
        return;
      }
      if (isHint(position)) {
        bindHint((TextView) holder.itemView);
      } else {
        bindIcon((IconCell) holder.itemView, position);
      }
    }

    private void bindHint (TextView view) {
      view.setText(R.string.ForumEditorIconsFree);
    }

    private void bindIcon (IconCell cell, int position) {
      long emojiId = 0;
      String description = Lang.getString(R.string.ForumEditorRegular);
      if (position > 1) {
        TdApi.Sticker sticker = visibleIcons.get(position - 2);
        emojiId = ((TdApi.StickerFullTypeCustomEmoji) sticker.fullType).customEmojiId;
        description = TextUtils.isEmpty(sticker.emoji) ?
          Lang.getString(R.string.ForumEditorCustomEmoji) : sticker.emoji;
      }
      boolean locked = !ForumTopicPolicy.allowedIcon(emojiId, false, defaultIcons);
      cell.bind(emojiId, form.submittedName(), emojiId == form.emoji(), locked, description);
      cell.setEnabled(form.editable());
      final long selected = emojiId;
      cell.setOnClickListener(v -> chooseIcon(selected));
    }

    @Override public void onViewRecycled (@NonNull RecyclerView.ViewHolder holder) {
      if (holder.itemView instanceof IconCell) {
        ((IconCell) holder.itemView).clearEmoji();
      }
    }
  }

  /** Uses the same custom-emoji Text/receiver pipeline as topic rows, with selector semantics. */
  private final class IconCell extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path tail = new Path();
    private final ComplexReceiver receiver = new ComplexReceiver(this);
    private Text emoji;
    private long emojiId;
    private String letter = "";
    private boolean locked;
    private final int iconSize;

    IconCell (Context context) {
      this(context, 28);
    }

    IconCell (Context context, int iconSize) {
      super(context);
      this.iconSize = iconSize;
      cells.add(this);
      setFocusable(true);
      setMinimumHeight(Screen.dp(48));
      setBackground(Theme.fillingSelector());
    }

    void bind (long id, String name, boolean selected, boolean locked, String description) {
      if (emojiId != id || id != 0 && emoji == null) {
        clearEmoji();
        emojiId = id;
        if (id != 0) {
          TdApi.FormattedText text =
            new TdApi.FormattedText(
              "*",
              new TdApi.TextEntity[] {
                new TdApi.TextEntity(
                  0,
                  1,
                  new TdApi.TextEntityTypeCustomEmoji(id)
                )
              }
            );
          emoji = new Text.Builder(
            tdlib,
            text,
            null,
            Screen.dp(Math.max(48, iconSize * 2)),
            Paints.robotoStyleProvider(iconSize),
            (TextColorSetThemed) () -> ColorId.icon,
            (value, media) -> {
              if (value == emoji) {
                value.requestMedia(receiver);
                invalidate();
              }
            }
          ).singleLine().build();
          emoji.requestMedia(receiver);
        }
      }
      letter = name.isEmpty() ? "" : name.substring(0, name.offsetByCodePoints(0, 1));
      this.locked = locked;
      setSelected(selected);
      setContentDescription(
        selected ? Lang.getString(R.string.ForumEditorSelected, description) :
          locked ? Lang.getString(R.string.ForumEditorLocked, description) : description
      );
      invalidate();
    }

    @Override protected void onDraw (Canvas canvas) {
      super.onDraw(canvas);
      float cx = getWidth() / 2f, cy = getHeight() / 2f;
      if (isSelected()) {
        paint.setColor(ColorUtils.alphaColor(.1f, Theme.getColor(ColorId.textLink)));
        DrawAlgorithms.drawRoundRect(canvas, Screen.dp(4), Screen.dp(3), Screen.dp(3),
          getWidth() - Screen.dp(3), getHeight() - Screen.dp(3), paint);
        paint.setColor(Theme.getColor(ColorId.textLink));
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Screen.dp(1.5f));
        DrawAlgorithms.drawRoundRect(canvas, Screen.dp(4), Screen.dp(3), Screen.dp(3),
          getWidth() - Screen.dp(3), getHeight() - Screen.dp(3), paint);
        paint.setStyle(Paint.Style.FILL);
      }
      if (emoji != null) {
        emoji.draw(
          canvas,
          (int) (cx - emoji.getWidth()/2f),
          (int) (cy - emoji.getHeight()/2f),
          null,
          locked ? .55f : 1f,
          receiver
        );
      } else {
        float r = Screen.dp(this == preview ? 24 : 16);
        paint.setColor(
          0xff000000 |
            (form != null ? form.color() : ForumTopicPolicy.ICON_COLORS[0])
        );
        DrawAlgorithms.drawRoundRect(
          canvas,
          Screen.dp(11),
          cx-r,
          cy-r,
          cx+r,
          cy+r-Screen.dp(3),
          paint
        );
        tail.reset();
        tail.moveTo(cx-r+Screen.dp(3), cy+r-Screen.dp(8));
        tail.lineTo(cx-r+Screen.dp(3), cy+r+Screen.dp(2));
        tail.lineTo(cx-r+Screen.dp(15), cy+r-Screen.dp(4));
        tail.close();
        canvas.drawPath(tail, paint);
        paint.setColor(Theme.getColor(ColorId.white));
        paint.setTextSize(Screen.dp(20));
        paint.setTypeface(Fonts.getRobotoMedium());
        paint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText(letter, cx, cy-Screen.dp(2)-(paint.ascent()+paint.descent())/2, paint);
      }
      if (locked) {
        paint.setColor(Theme.getColor(ColorId.iconLight));
        float x = getWidth()-Screen.dp(12), y = getHeight()-Screen.dp(12);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Screen.dp(1.5f));
        DrawAlgorithms.drawRoundRect(
          canvas,
          Screen.dp(3),
          x-Screen.dp(3),
          y-Screen.dp(7),
          x+Screen.dp(3),
          y,
          paint
        );
        paint.setStyle(Paint.Style.FILL);
        DrawAlgorithms.drawRoundRect(
          canvas,
          Screen.dp(2),
          x-Screen.dp(5),
          y-Screen.dp(2),
          x+Screen.dp(5),
          y+Screen.dp(5),
          paint
        );
      }
    }

    @Override public void onInitializeAccessibilityNodeInfo (AccessibilityNodeInfo info) {
      super.onInitializeAccessibilityNodeInfo(info);
      info.setClassName("android.widget.RadioButton");
      info.setCheckable(true);
      info.setChecked(isSelected());
    }

    void clearEmoji () {
      if (emoji != null) {
        emoji.performDestroy();
      }
      emoji = null;
      emojiId = 0;
      receiver.clear();
    }

    void destroyCell () {
      clearEmoji();
      receiver.performDestroy();
    }

    @Override protected void onAttachedToWindow () {
      super.onAttachedToWindow();
      receiver.attach();
    }

    @Override protected void onDetachedFromWindow () {
      receiver.detach();
      super.onDetachedFromWindow();
    }
  }
}
