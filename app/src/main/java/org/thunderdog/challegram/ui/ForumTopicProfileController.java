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
import android.os.Bundle;
import android.os.Parcelable;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.annotation.NonNull;
import androidx.viewpager.widget.PagerAdapter;
import androidx.core.widget.NestedScrollView;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.component.base.SettingView;
import org.thunderdog.challegram.component.chat.ChatHeaderView;
import org.thunderdog.challegram.navigation.ViewPagerTopView;
import org.thunderdog.challegram.navigation.NavigationController;
import org.thunderdog.challegram.widget.ViewPager;
import org.thunderdog.challegram.widget.rtl.RtlViewPager;
import org.thunderdog.challegram.navigation.ViewPagerHeaderViewCompact;
import org.thunderdog.challegram.unsorted.Settings;

import me.vkryl.android.widget.FrameLayoutFix;
import org.thunderdog.challegram.support.ViewSupport;
import org.thunderdog.challegram.unsorted.Size;
import org.thunderdog.challegram.data.ForumPresentation;
// region @MadeByLLM("gpt-6-astra", id = "forum-topics")
import org.thunderdog.challegram.data.ForumTopicContext;
// endregion
import org.thunderdog.challegram.data.ForumTopicMedia;
import org.thunderdog.challegram.data.ForumTopicNotifications;
import org.thunderdog.challegram.data.ForumTopicPolicy;
import org.thunderdog.challegram.data.ForumTopicProfileLink;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.navigation.BackHeaderButton;
import org.thunderdog.challegram.navigation.HeaderView;
import org.thunderdog.challegram.navigation.HeaderButton;
import org.thunderdog.challegram.navigation.CounterHeaderView;
import org.thunderdog.challegram.navigation.Menu;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.telegram.ChatListener;
import org.thunderdog.challegram.telegram.CleanupStartupDelegate;
import org.thunderdog.challegram.telegram.ForumTopicActions;
import org.thunderdog.challegram.telegram.ForumTopicStore;
import org.thunderdog.challegram.telegram.MessageListener;
import org.thunderdog.challegram.telegram.NotificationSettingsListener;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibCache;
import org.thunderdog.challegram.telegram.TdlibForumTopicManager;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.tool.Views;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

import tgx.td.ChatId;
import tgx.td.Td;

/** Topic identity/settings plus existing shared-media pages, not a second group profile. */
@me.vkryl.annotation.MadeByLLM(
  value = "gpt-6.1-sol", others = {"gpt-6-astra", "GPT-6"}, id = "forum-native-screens", ref = "PR #1110"
)
public final class ForumTopicProfileController
  extends ViewController<ForumTopicProfileController.Arguments>
  implements Menu, ChatListener, MessageListener, NotificationSettingsListener,
    TdlibCache.SupergroupDataChangeListener, CleanupStartupDelegate,
    SharedBaseController.MediaSelectionDelegate {
  public static final class Arguments {
    public final long chatId;
    public final int forumTopicId;
    public final TdApi.ChatList chatList;

    public Arguments (long chatId, int forumTopicId, @Nullable TdApi.ChatList chatList) {
      if (chatId == 0 || forumTopicId <= 0) {
        throw new IllegalArgumentException("A typed forum topic is required");
      }
      this.chatId = chatId;
      this.forumTopicId = forumTopicId;
      this.chatList = chatList;
    }
  }

  private static final int[] TAB_NAMES =
    {
      R.string.TabMedia,
      R.string.MessageSearchFilterFiles,
      R.string.TabLinks,
      R.string.MessageSearchFilterMusic,
      R.string.ForumProfilePolls,
      R.string.TabGifs,
      R.string.TabVoiceMessages
    };
  private final ForumTopicMedia.Category[] categories =
    new ForumTopicMedia.Category[ForumTopicMedia.CATEGORY_COUNT];
  private final SharedBaseController<?>[] pages = new SharedBaseController<?
    >[ForumTopicMedia.CATEGORY_COUNT];
  private final Parcelable[] restoredPageStates = new Parcelable[ForumTopicMedia.CATEGORY_COUNT];
  private final ForumTopicProfileLink profileLink = new ForumTopicProfileLink();
  private List<Integer> visibleTabs = Collections.emptyList();
  private int activeTab = -1, restoreTab = -1, restoreScroll;
  private String query = "";
  private boolean restoreSearch, restoreScrollPending;
  private boolean busy, subscribed, materialsStarted, refreshScheduled, terminal;
  // region @MadeByLLM("gpt-6-astra", id = "forum-topics")
  private boolean returnToTopics;
  // endregion
  private volatile Object epoch = new Object();
  private ForumTopicStore.TopicSubscription subscription;
  private TdApi.ForumTopic topic;
  private TdApi.Error metadataError;
  private NestedScrollView scroll;
  private float headerFactor = (float) Size.getHeaderSizeDifference(false) /
    Size.getHeaderSizeDifference(true);
  private LinearLayout actions;
  private ViewPagerTopView tabs;
  private FrameLayoutFix tabWrap, selectionBar;
  private CounterHeaderView selectionCounter;
  private SharedBaseController<?> selectionPage;
  private final List<HeaderButton> selectionButtons = new ArrayList<>(5);
  private float selectionFactor;
  private ViewPagerHeaderViewCompact tabScroll;
  private RtlViewPager materialFrame;
  private MediaPagerAdapter mediaAdapter;
  private boolean updatingPages;
  private int pagerScrollState;
  private ChatHeaderView headerCell;
  private SettingView group, notifications, linkRow;
  private TextView state, materialState;
  private View editButton, moreButton;
  private static final int MAX_RECENT_CONTENT = 128, MAX_PENDING_CONTENT =
    32, MAX_CONTENT_REQUESTS = 2;
  private final Map<Long, TdApi.Message> recentContentMessages = new LinkedHashMap<>();
  private final Map<Long, TdApi.MessageContent> pendingContent =
    new LinkedHashMap<>(), activeContent = new LinkedHashMap<>();
  private final Map<Long, Long> recentContentRequests = new LinkedHashMap<>();
  private boolean contentPumpScheduled;

  public ForumTopicProfileController (Context context, Tdlib tdlib) {
    super(context, tdlib);
    for (int i = 0; i < categories.length; i++) {
      categories[i] = new ForumTopicMedia.Category();
    }
  }

  @Override public int getId () {
    return R.id.controller_forumTopicProfile;
  }

  @Override public long getChatId () {
    return getArgumentsStrict().chatId;
  }

  public int getForumTopicId () {
    return getArgumentsStrict().forumTopicId;
  }

  public TdApi.ChatList getChatList () {
    return getArgumentsStrict().chatList;
  }

  public TdApi.MessageTopicForum getTopicId () {
    return new TdApi.MessageTopicForum(getForumTopicId());
  }

  private TdlibForumTopicManager.Key key () {
    return new TdlibForumTopicManager.Key(getChatId(), getForumTopicId());
  }

  @Override public CharSequence getName () {
    return Lang.getString(R.string.ForumTopicTitle);
  }

  @Override protected int getBackButton () {
    return BackHeaderButton.TYPE_BACK;
  }

  @Override public View getCustomHeaderCell () {
    return headerCell;
  }

  @Override protected int getHeaderHeight () {
    return Size.getHeaderPortraitSize() +
      (int) (Size.getHeaderSizeDifference(true) * headerFactor);
  }

  @Override protected int getMaximumHeaderHeight () {
    return Size.getHeaderBigPortraitSize(false);
  }

  @Override protected int getFloatingButtonId () {
    return R.drawable.baseline_chat_bubble_24;
  }

  @Override protected void onFloatingButtonPressed () {
    if (ready()) {
      navigateBack();
    }
  }

  @Override protected boolean allowMenuReuse () {
    return false;
  }

  @Override protected int getMenuId () {
    return R.id.menu_forumTopicProfile;
  }

  @Override protected int getSearchMenuId () {
    return R.id.menu_clear;
  }

  @Override public boolean supportsBottomInset () {
    return true;
  }

  @Override protected void onBottomInsetChanged (int inset, int withoutIme, boolean isIme) {
    super.onBottomInsetChanged(inset, withoutIme, isIme);
    Views.applyBottomInset(scroll, inset);
  }

  @Override protected View onCreateView (Context context) {
    headerCell = new ChatHeaderView(context, tdlib, this);
    headerCell.initWithController(this, true);
    headerCell.setInnerMargins(Screen.dp(56), Screen.dp(147));
    headerCell.setInnerRightMarginStart(Screen.dp(49));
    headerCell.setBackground(null);
    scroll = new NestedScrollView(context);
    scroll.setFillViewport(true);
    // Keep initial focus on the profile, not on a newly attached shared-media RecyclerView.
    // Descendant actions remain focusable for keyboard and accessibility navigation.
    scroll.setDescendantFocusability(ViewGroup.FOCUS_BEFORE_DESCENDANTS);
    scroll.setFocusableInTouchMode(true);
    scroll.requestFocus();
    scroll.setOnScrollChangeListener(
      (NestedScrollView.OnScrollChangeListener) (v, x, y, oldX,
        oldY) -> {
        if (y != oldY) {
          restoreScrollPending = false;
          updateHeader(y);
        }
      }
    );
    Views.applyBottomInset(scroll, extraBottomInset);
    scroll.setBackgroundColor(Theme.backgroundColor());
    addThemeBackgroundColorListener(scroll, ColorId.background);
    LinearLayout content = new LinearLayout(context);
    content.setOrientation(LinearLayout.VERTICAL);
    content.setPadding(0, Size.getHeaderSizeDifference(false), 0, 0);
    scroll.addView(
      content,
      new ViewGroup.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
      )
    );
    LinearLayout profile = new LinearLayout(context);
    profile.setOrientation(LinearLayout.VERTICAL);
    profile.setLayoutDirection(Lang.rtl() ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR);
    int pad = Screen.dp(16);
    content.addView(profile);
    actions = new LinearLayout(context);
    actions.setOrientation(LinearLayout.VERTICAL);
    profile.addView(actions, new LinearLayout.LayoutParams(-1, -2));
    group = setting(SettingView.TYPE_INFO, R.drawable.baseline_group_24);
    group.setOnClickListener(v -> tdlib.ui().openChatProfile(this, getChatId(), null, null));
    actions.addView(group);
    notifications = setting(SettingView.TYPE_INFO, R.drawable.baseline_notifications_24);
    notifications.setName(R.string.Notifications);
    notifications.setOnClickListener(v -> {
      if (ready() && !busy) {
        toggleMute();
      }
    });
    LinearLayout.LayoutParams notificationParams = new LinearLayout.LayoutParams(-1, Screen.dp(76));
    notificationParams.topMargin = Screen.dp(12);
    notificationParams.bottomMargin = Screen.dp(12);
    actions.addView(notifications, notificationParams);
    state = text(14, ColorId.textLight);
    state.setPadding(pad, Screen.dp(12), pad, Screen.dp(12));
    state.setOnClickListener(v -> retryMetadata());
    profile.addView(state, new LinearLayout.LayoutParams(-1, -2));
    createLinkRow(profile);
    tabScroll = new ViewPagerHeaderViewCompact(context);
    tabs = tabScroll.getTopView();
    tabs.setUseDarkBackground();
    tabs.setTextFromToColorId(ColorId.textLight, ColorId.profileSectionActiveContent);
    tabs.setSelectionColorId(ColorId.profileSectionActive);
    tabs.setShowLabelOnActiveOnly(!Settings.instance().needReduceMotion());
    tabs.setOnItemClickListener(index -> selectTab(visibleTabs.get(index), true));
    tabs.setLayoutParams(FrameLayoutFix.newParams(-2, -1));
    tabScroll.getRecyclerView().setLayoutParams(FrameLayoutFix.newParams(-1, -1));
    addThemeInvalidateListener(tabs);
    ViewSupport.setThemedBackground(tabScroll, ColorId.filling, this);
    tabWrap = new FrameLayoutFix(context);
    tabWrap.addView(tabScroll, FrameLayoutFix.newParams(-1, -1));
    LinearLayout.LayoutParams tabParams = new LinearLayout.LayoutParams(-1, Screen.dp(48));
    tabParams.topMargin = Screen.dp(12);
    content.addView(tabWrap, tabParams);
    materialState = text(16, ColorId.textLight);
    materialState.setGravity(Gravity.CENTER);
    materialState.setMinHeight(Screen.dp(64));
    materialState.setPadding(pad, pad, pad, pad);
    materialState.setOnClickListener(v -> refreshMaterials());
    content.addView(materialState, new LinearLayout.LayoutParams(-1, -2));
    materialFrame = new RtlViewPager(context);
    materialFrame.setOverScrollMode(View.OVER_SCROLL_NEVER);
    materialFrame.setAdapter(mediaAdapter = new MediaPagerAdapter());
    materialFrame.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {
      @Override public void onPageScrolled (int position, float offset, int offsetPixels) {
        if (!updatingPages && !visibleTabs.isEmpty()) {
          tabs.setSelectionFactor(position + ViewPager.clampPositionOffset(offset));
        }
      }

      @Override public void onPageSelected (int position) {
        if (!updatingPages && position >= 0 && position < visibleTabs.size()) {
          activateTab(visibleTabs.get(position));
        }
      }

      @Override public void onPageScrollStateChanged (int state) {
        pagerScrollState = state;
        if (state != ViewPager.SCROLL_STATE_SETTLING) {
          tabs.resetFromTo();
        }
      }
    });
    content.addView(materialFrame, new LinearLayout.LayoutParams(-1, Screen.dp(360)));
    scroll.addOnLayoutChangeListener(
      (v, l, t, r, b, ol, ot, or, ob) -> {
        if (b - t != ob - ot) {
          // On API 16-17, requesting a child layout during layout leaves ancestors dirty
          // without scheduling another traversal, blocking later visibility updates.
          scroll.post(
            () -> {
              if (isDestroyed()) {
                return;
              }
              int height = Math.max(Screen.dp(200), scroll.getHeight() - Screen.dp(56));
              ViewGroup.LayoutParams params = materialFrame.getLayoutParams();
              if (params.height != height) {
                params.height = height;
                materialFrame.setLayoutParams(params);
              }
            }
          );
        }
      }
    );
    tdlib.listeners().subscribeToChatUpdates(getChatId(), this);
    tdlib.listeners().subscribeToMessageUpdates(getChatId(), this);
    tdlib.listeners().subscribeToSettingsUpdates(this);
    tdlib.listeners().addCleanupListener(this);
    tdlib.cache().subscribeToSupergroupUpdates(ChatId.toSupergroupId(getChatId()), this);
    subscribed = true;
    observeTopic();
    render();
    loadLink();
    if (restoreScrollPending) {
      restoreScrollAfterLayout();
    }
    return scroll;
  }

  private void updateHeader (int scrollY) {
    if (inSearchMode()) {
      return;
    }
    headerFactor = Math.max(0f, (float) (Size.getHeaderSizeDifference(false) - scrollY) /
      Size.getHeaderSizeDifference(true));
    headerCell.setInnerRightMargin(Screen.dp(headerFactor == 0f ? 147 : 16));
    headerCell.setInnerRightMarginStart(Screen.dp(headerFactor == 0f ? 147 : 16));
    headerCell.setScaleFactor(headerFactor, headerFactor, headerFactor, true);
    if (floatingButton != null) {
      floatingButton.setHeightFactor(headerFactor, 0f, true);
    }
    if (headerView != null && isFocused()) {
      headerView.setBackgroundHeight(getHeaderHeight());
    }
  }

  private void restoreScrollAfterLayout () {
    scroll.getViewTreeObserver()
      .addOnPreDrawListener(
        new ViewTreeObserver.OnPreDrawListener() {
        @Override public boolean onPreDraw () {
          if (restoreScrollPending && !isDestroyed() && !terminal) {
            if (!ready() || !materialsStarted) {
              return true;
            }
            // Wait for the restored page (or a settled empty/error result), not the loading header.
            if (restoreTab >= 0 || activeTab < 0) {
              for (ForumTopicMedia.Category category : categories) {
                if (category.availability == ForumTopicMedia.Availability.LOADING) {
                  return true;
                }
              }
            }
          }
          scroll.getViewTreeObserver().removeOnPreDrawListener(this);
          if (restoreScrollPending && !isDestroyed()) {
            restoreScrollPending = false;
            scroll.scrollTo(0, restoreScroll);
            restoreScroll = 0;
          }
          return true;
        }
      }
      );
  }

  private TextView text (float size, int color) {
    TextView view = new TextView(context);
    view.setTextSize(size);
    view.setTypeface(Fonts.getRobotoRegular());
    view.setTextColor(Theme.getColor(color));
    addThemeTextColorListener(view, color);
    return view;
  }

  private SettingView setting (int type, int icon) {
    SettingView view = new SettingView(context, tdlib);
    view.setType(type);
    view.setSwapDataAndName();
    view.setIcon(icon);
    view.addThemeListeners(this);
    return view;
  }

  private void createLinkRow (LinearLayout profile) {
    linkRow = setting(SettingView.TYPE_INFO, R.drawable.baseline_link_24);
    linkRow.setOnClickListener(v -> copyLink());
    profile.addView(linkRow);
    renderLink();
  }

  private void observeTopic () {
    if (subscription != null) {
      subscription.close();
    }
    Object ticket = epoch;
    subscription = tdlib.topics()
      .observeTopic(
        key(),
        (value, error) -> {
        if (isDestroyed() || epoch != ticket) {
          return;
        }
        topic = value;
        metadataError = error;
        // region @MadeByLLM("gpt-6-astra", id = "forum-topics")
        terminal = ForumTopicContext.isUnavailable(error);
        returnToTopics = terminal;
        // endregion
        render();
        // region @MadeByLLM("gpt-6-astra", id = "forum-topics")
        if (returnToTopics) {
          returnToTopics(8);
        }
        // endregion
        if (ready() && !materialsStarted) {
          materialsStarted = true;
          refreshMaterials();
        }
      }
      );
  }

  private void retryMetadata () {
    if (terminal || busy) {
      return;
    }
    metadataError = null;
    tdlib.topics().retryTopic(key());
    render();
  }

  private TdApi.ForumTopic currentTopic () {
    TdlibForumTopicManager.Entry entry = tdlib.topics().find(key());
    return entry != null ? entry.value : null;
  }

  private boolean ready () {
    return !isDestroyed() &&
      !terminal &&
      metadataError == null &&
      topic != null &&
      tdlib.chatAvailable(tdlib.chat(getChatId()));
  }

  private boolean canEdit () {
    TdApi.ForumTopic current = currentTopic();
    return ready() &&
      current != null &&
      ForumTopicPolicy.canEdit(tdlib.chatStatus(getChatId()), current.info);
  }



  @me.vkryl.annotation.MadeByLLM(value = "gpt-6-astra", id = "forum-topics")
  private CharSequence profileTitle () {
    if (terminal) {
      return Lang.getString(R.string.ForumTopicUnavailable);
    }
    return topic != null ? topic.info.name :
      Lang.getString(metadataError != null ? R.string.ForumTopicTitle : R.string.ForumTopicLoading);
  }

  @me.vkryl.annotation.MadeByLLM(value = "gpt-6-astra", id = "forum-topics")
  private void returnToTopics (int retries) {
    if (!returnToTopics || isDestroyed() || !isFocused()) {
      return;
    }
    ForumTabsNavigation.Result result =
      ForumTabsNavigation.returnFromUnavailableTopic(this, getForumTopicId(), true);
    if (result == ForumTabsNavigation.Result.RETRY && retries > 0) {
      Object ticket = epoch;
      tdlib.ui()
        .postDelayed(
          () -> {
          if (epoch == ticket) {
            returnToTopics(retries - 1);
          }
        },
          150
        );
    }
  }

  @Override
  @me.vkryl.annotation.MadeByLLM(value = "gpt-6-astra", id = "forum-topics")
  public boolean performOnBackPressed (boolean fromTop, boolean commit) {
    if (selectionPage != null) {
      if (commit) {
        selectionPage.setInMediaSelectMode(false);
      }
      return true;
    }
    if (returnToTopics &&
      ForumTabsNavigation.returnFromUnavailableTopic(this, getForumTopicId(), commit) ==
        ForumTabsNavigation.Result.RETURNED) {
      return true;
    }
    return super.performOnBackPressed(fromTop, commit);
  }

  private void render () {
    if (headerCell == null || group == null) {
      return;
    }
    boolean available = ready();
    group.setName(tdlib.chatTitle(getChatId()));
    group.setData(R.string.Group);
    TdApi.Chat chat = tdlib.chat(getChatId());
    if (chat != null) {
      headerCell.setChat(tdlib, chat, null);
      headerCell.setForumTopic(chat, getTopicId(), topic, null);
      headerCell.setTitle(profileTitle().toString());
      headerCell.setNoStatus(true);
    }
    // region @MadeByLLM("gpt-6-astra", id = "forum-topics")
    state.setEnabled(!terminal && metadataError != null && !busy);
    // endregion
    state.setText(
      Lang.getString(
        terminal ||
          topic != null &&
            !tdlib.chatAvailable(tdlib.chat(getChatId())) ? R.string.ForumProfileUnavailable :
          metadataError != null ? R.string.ForumTopicLoadFailed : topic == null ?
            R.string.ForumTopicLoading : topic.info.isClosed ? R.string.ForumTopicClosed :
            topic.info.isGeneral ? R.string.ForumProfileGeneralHint : R.string.ForumTopicTitle
      )
    );
    state.setVisibility(
      available &&
        !topic.info.isClosed &&
        !topic.info.isGeneral ? View.GONE : View.VISIBLE
    );
    actions.setVisibility(available ? View.VISIBLE : View.GONE);
    notifications.setEnabled(available && !busy && topic != null && topic.notificationSettings != null);
    if (topic != null) {
      boolean muted =
        ForumPresentation.isMuted(topic.notificationSettings, tdlib.chatMuteFor(getChatId()) > 0);
      notifications.setData(muted ? R.string.NotificationsDisabled : R.string.NotificationsEnabled);
      notifications.setIcon(muted ? R.drawable.baseline_notifications_off_24 :
        R.drawable.baseline_notifications_24);
    }
    if (editButton != null) {
      editButton.setVisibility(canEdit() ? View.VISIBLE : View.GONE);
      editButton.setEnabled(!busy);
    }
    if (moreButton != null) {
      moreButton.setEnabled(available && !busy);
    }
    if (!available) {
      resetMediaSelection();
      materialFrame.setVisibility(View.GONE);
      tabWrap.setVisibility(View.GONE);
      materialState.setVisibility(View.GONE);
    } else {
      renderTabs();
    }
    if (available && !profileLink.matches(getChatId(), getForumTopicId())) {
      loadLink();
    }
    renderLink();
  }

  @Override public void fillMenuItems (int id, HeaderView header, LinearLayout menu) {
    if (id == R.id.menu_clear) {
      header.addClearButton(menu, this);
      return;
    }
    if (id != R.id.menu_forumTopicProfile) {
      return;
    }
    header.addSearchButton(menu, this);
    header.addButton(
      menu,
      R.id.forum_profile_edit,
      R.drawable.baseline_edit_24,
      getHeaderIconColorId(),
      this,
      Screen.dp(49)
    );
    header.addButton(
      menu,
      R.id.forum_profile_more,
      R.drawable.baseline_more_vert_24,
      getHeaderIconColorId(),
      this,
      Screen.dp(49)
    );
    editButton = menu.findViewById(R.id.forum_profile_edit);
    moreButton = menu.findViewById(R.id.forum_profile_more);
    editButton.setContentDescription(Lang.getString(R.string.ForumEditTopic));
    moreButton.setContentDescription(Lang.getString(R.string.ForumProfileMore));
    render();
  }

  @Override public void onMenuItemPressed (int id, View view) {
    if (id == R.id.menu_btn_clear) {
      clearSearchInput();
      return;
    }
    if (id == R.id.menu_btn_search) {
      if (ready()) {
        openSearchMode();
      }
      return;
    }
    if (busy || !ready()) {
      return;
    }
    if (id == R.id.forum_profile_edit) {
      openEditor();
    } else if (id == R.id.forum_profile_more) {
      showOverflow();
    }
  }

  @Override protected void onSearchInputChanged (String text) {
    super.onSearchInputChanged(text);
    query = text;
    if (activeTab >= 0 && pages[activeTab] != null) {
      pages[activeTab].search(text);
    }
  }

  @Override protected void onLeaveSearchMode () {
    onSearchInputChanged("");
    updateHeader(scroll.getScrollY());
  }

  private void openEditor () {
    if (!canEdit()) {
      return;
    }
    ForumTopicEditController.open(this, getChatId(), getForumTopicId());
  }

  private void run (Consumer<ForumTopicActions.Callback<TdApi.Ok>> operation) {
    if (busy || !ready()) {
      return;
    }
    busy = true;
    render();
    Object ticket = epoch;
    operation.accept(
      (ok, error) -> {
        if (isDestroyed() || epoch != ticket) {
          return;
        }
        busy = false;
        render();
        if (error != null) {
          error(error.message);
        }
      }
    );
  }

  private void error (String error) {
    if (!isDestroyed()) {
      showAlert(
        new AlertDialog.Builder(
          context,
          Theme.dialogTheme()
        )
          .setTitle(getName())
          .setMessage(error)
          .setPositiveButton(
            Lang
              .getString(R.string.OK),
            null
          )
      );
    }
  }

  private void toggleMute () {
    TdApi.ForumTopic current = currentTopic();
    if (current == null || current.notificationSettings == null) {
      return;
    }
    run(
      done ->
        tdlib.topics().actions.setNotifications(
          key(),
          ForumTopicNotifications.toggleMute(
            current.notificationSettings,
            tdlib.chatMuteFor(getChatId()) > 0
          ),
          done
        )
    );
  }

  private void copyLink () {
    if (!ready()) {
      return;
    }
    if (profileLink.matches(getChatId(), getForumTopicId()) &&
      profileLink.state() == ForumTopicProfileLink.State.READY) {
      UI.copyText(profileLink.url(), R.string.CopiedLink);
    } else if (profileLink.state() != ForumTopicProfileLink.State.LOADING) {
      loadLink();
    }
  }

  private void loadLink () {
    if (isDestroyed() || terminal || !tdlib.chatAvailable(tdlib.chat(getChatId()))) {
      return;
    }
    ForumTopicProfileLink.Request request = profileLink.begin(getChatId(), getForumTopicId());
    if (request == null) {
      return;
    }
    Object ticket = epoch;
    renderLink();
    // The existing helper uses read-only GetForumTopicLink with the typed forum topic ID.
    tdlib.topics().actions.getLink(
      new TdlibForumTopicManager.Key(
        request.chatId,
        request.forumTopicId
      ),
      (link, error) -> tdlib.ui()
        .post(
          () -> {
        if (!acceptLinkResult(ticket, request)) {
          return;
        }
        if (profileLink.complete(
          request,
          error == null &&
            link != null ? link.link : null,
          error == null &&
            link != null &&
            link.isPublic
        )) {
          renderLink();
        }
      }
        )
    );
    tdlib.ui()
      .postDelayed(
        () -> {
        if (acceptLinkResult(ticket, request) && profileLink.complete(request, null, false)) {
          renderLink();
        }
      },
        15000
      );
  }

  private boolean acceptLinkResult (Object ticket, ForumTopicProfileLink.Request request) {
    return !isDestroyed() &&
      !terminal &&
      epoch == ticket &&
      getChatId() == request.chatId &&
      getForumTopicId() == request.forumTopicId;
  }

  private void renderLink () {
    if (linkRow == null) {
      return;
    }
    boolean unavailable = terminal ||
      metadataError != null ||
      !tdlib.chatAvailable(tdlib.chat(getChatId()));
    boolean hasLink = !unavailable &&
      profileLink.matches(getChatId(), getForumTopicId()) &&
      profileLink.state() == ForumTopicProfileLink.State.READY;
    boolean failed = unavailable || profileLink.state() == ForumTopicProfileLink.State.UNAVAILABLE;
    linkRow.setLayoutDirection(
      Lang.rtl() ? View.LAYOUT_DIRECTION_RTL :
        View.LAYOUT_DIRECTION_LTR
    );
    CharSequence value = hasLink ? profileLink.displayUrl() :
      Lang.getString(failed ? R.string.NoLinkInfo : R.string.LoadingInformation);
    CharSequence label = Lang.getString(
      failed && ready() ? R.string.FirebaseErrorResolveTryAgain :
        hasLink && !profileLink.isPublic() ? R.string.ForumProfileTopicLink : R.string.InviteLink);
    linkRow.setName(value);
    linkRow.setData(label);
    linkRow.setEnabled(ready() && (hasLink || failed));
    linkRow.setContentDescription(Lang.getString(R.string.ForumProfileSettingValue, label, value));
  }

  private static void add (
    List<String> labels,
    List<Runnable> actions,
    int label,
    Runnable action
  ) {
    labels.add(Lang.getString(label));
    actions.add(action);
  }

  private void showOverflow () {
    if (!ready() || busy) {
      return;
    }
    TdApi.ForumTopic current = currentTopic();
    if (current == null) {
      return;
    }
    List<String> labels = new ArrayList<>();
    List<Runnable> actions = new ArrayList<>();
    if (ForumTopicPolicy.isMember(tdlib.chatStatus(getChatId()))) {
      add(
        labels,
        actions,
        R.string.LeaveMegaMenu,
        () -> {
          if (ForumTopicPolicy.isMember(tdlib.chatStatus(getChatId()))) {
            tdlib.ui()
              .processLeaveButton(
                this,
                getChatList(),
                getChatId(),
                R.id.btn_removeChatFromList,
                () -> tdlib.ui().exitToChatScreen(this, getChatId())
              );
          }
        }
      );
    }
    if (canDeleteGroup()) {
      add(labels, actions, R.string.ForumProfileDeleteGroup, this::deleteGroup);
    }
    // region @MadeByLLM("gpt-6.1-sol", id = "forum-header-popup")
    if (labels.isEmpty()) {
      return;
    }
    Object ticket = epoch;
    showMore(labels.toArray(new String[0]), index -> {
      if (epoch == ticket && ready() && !busy) {
        actions.get(index).run();
      }
    });
    // endregion
  }

  private boolean canDeleteGroup () {
    TdApi.Chat chat = tdlib.chat(getChatId());
    return ready() && chat != null && chat.canBeDeletedForAllUsers;
  }

  private void deleteGroup () {
    if (!canDeleteGroup()) {
      return;
    }
    showAlert(
      new AlertDialog.Builder(
        context,
        Theme.dialogTheme()
      )
        .setTitle(Lang.getString(R.string.ForumProfileDeleteGroup))
        .setMessage(
          Lang.getString(
            R.string.ForumProfileDeleteGroupConfirm,
            tdlib.chatTitle(getChatId())
          )
        )
        .setNegativeButton(
          Lang.getString(R.string.Cancel),
          null
        )
        .setPositiveButton(
          Lang.getString(R.string.Delete),
          (dialog, which) -> {
            if (!canDeleteGroup()) {
              return;
            }
            showAlert(
              new AlertDialog.Builder(
                context,
                Theme.dialogTheme()
              )
                .setTitle(tdlib.chatTitle(getChatId()))
                .setMessage(Lang.getString( R .string.DestroyGroupHint ))
                .setNegativeButton(
                  Lang.getString(R.string.Cancel),
                  null
                )
                .setPositiveButton(
                  Lang.getString(R.string.ForumProfileDeleteGroup),
                  (lastDialog, lastWhich) -> {
                if (!canDeleteGroup() || busy) {
                  return;
                }
                run(
                  done ->
                    tdlib.send(
                      new TdApi.DeleteChat(getChatId()),
                      (ok, error) -> tdlib.ui()
                        .post(
                          () -> {
                        done.onResult(ok, error);
                        if (error == null && !isDestroyed()) {
                          tdlib.ui().exitToChatScreen(this, getChatId());
                        }
                      }
                        )
                    )
                );
              }
                )
            );
          }
        )
    );
  }

  private void refreshMaterials () {
    if (!ready()) {
      return;
    }
    for (int i = 0; i < categories.length; i++) {
      refreshCategory(i);
    }
    renderTabs();
  }

  private void refreshCategory (int index) {
    ForumTopicMedia.Category category = categories[index];
    int ticket = category.begin();
    Object lifecycle = epoch;
    tdlib.send(
      new TdApi.GetChatMessageCount(
        getChatId(),
        getTopicId(),
        ForumTopicMedia.filter(index),
        false
      ),
      (count, error) -> tdlib.ui()
        .post(
          () -> {
        if (isDestroyed() || epoch != lifecycle) {
          return;
        }
        category.count(ticket, count != null ? count.count : -1, error);
      }
        )
    );
    probeCategory(index, ticket, lifecycle, 0, true);
    tdlib.ui()
      .postDelayed(
        () -> {
        if (!isDestroyed() &&
          epoch == lifecycle &&
          category.availability == ForumTopicMedia.Availability.LOADING &&
          category.confirm(
              ticket,
              false,
              new TdApi.Error(408, "Material availability request timed out")
            )) {
          renderTabs();
        }
      },
        15000
      );
  }

  private void probeCategory (
    int index,
    int ticket,
    Object lifecycle,
    long offset,
    boolean mayRetry
  ) {
    ForumTopicMedia.Category category = categories[index];
    // At most two one-message pages. A partial/unknown result is not a reason to scan history.
    tdlib.send(
      new TdApi.SearchChatMessages(
        getChatId(),
        getTopicId(),
        "",
        null,
        offset,
        0,
        1,
        ForumTopicMedia.filter(index)
      ),
      (messages, error) -> tdlib.ui()
        .post(
          () -> {
        if (isDestroyed() || epoch != lifecycle || !category.isCurrent(ticket)) {
          return;
        }
        boolean found = false;
        if (messages != null) {
          for (TdApi.Message message : messages.messages) {
            if (ForumTopicMedia.matches(getChatId(), getTopicId(), message) &&
              ProfileController.filterMediaMessage(message) &&
              Td.matchesFilter(message, ForumTopicMedia.filter(index))) {
              found = true;
              break;
            }
          }
        }
        if (error == null && messages != null && !found) {
          if (messages.nextFromMessageId != 0 && messages.nextFromMessageId != offset && mayRetry) {
            probeCategory(index, ticket, lifecycle, messages.nextFromMessageId, false);
            return;
          }
          if (messages.totalCount < 0 || messages.nextFromMessageId != 0) {
            if (category.unknown(ticket)) {
              renderTabs();
            }
            return;
          }
        }
        if (category.confirm(ticket, found, error)) {
          renderTabs();
        }
      }
        )
    );
  }

  private void renderTabs () {
    if (tabs == null || !ready()) {
      return;
    }
    List<Integer> visible = ForumTopicMedia.visible(categories);
    int selected = ForumTopicMedia.nearest(visible, activeTab);
    if (restoreTab >= 0 && visible.contains(restoreTab)) {
      selected = restoreTab;
      restoreTab = -1;
    }
    if (selectionPage != null && !visible.contains(activeTab)) {
      resetMediaSelection();
    }
    if (!visible.equals(visibleTabs)) {
      visibleTabs = visible;
      tabs.resetFromTo();
      List<ViewPagerTopView.Item> items = new ArrayList<>(visible.size());
      for (int index : visible) {
        SharedBaseController<?> page = mediaPage(index);
        items.add(new ViewPagerTopView.Item(
          Lang.uppercase(Lang.getString(TAB_NAMES[index])), page.getIcon(), null));
      }
      tabs.setItems(items);
      updatingPages = true;
      mediaAdapter.notifyDataSetChanged();
      int position = visibleTabs.indexOf(selected);
      if (position >= 0) {
        materialFrame.setCurrentItem(position, false);
      }
      updatingPages = false;
    }
    tabWrap.setVisibility(visible.isEmpty() ? View.GONE : View.VISIBLE);
    boolean loading = false, failed = false, unknown = false;
    for (ForumTopicMedia.Category category : categories) {
      loading |= category.availability == ForumTopicMedia.Availability.LOADING;
      failed |= category.error != null ||
        category.availability == ForumTopicMedia.Availability.ERROR;
      unknown |= category.availability == ForumTopicMedia.Availability.UNKNOWN;
    }
    materialState.setVisibility(visible.isEmpty() || failed || unknown ? View.VISIBLE : View.GONE);
    materialState.setText(
      Lang.getString(
        failed ? tdlib.isConnected() ?
          R.string.ForumProfileMediaRetry : R.string.ForumProfileMediaOffline :
          loading ? R.string.ForumProfileMediaLoading : unknown ?
            R.string.ForumProfileMediaUnknown :
            R.string.ForumProfileMediaEmpty
      )
    );
    materialFrame.setVisibility(visible.isEmpty() ? View.GONE : View.VISIBLE);
    if (selected != activeTab || selected >= 0 && materialFrame.getCurrentItem() != visibleTabs.indexOf(selected)) {
      selectTab(selected);
    } else {
      updateSelectedTab();
    }
  }

  private SharedBaseController<?> mediaPage (int index) {
    SharedBaseController<?> page = pages[index];
    if (page == null) {
      page = SharedBaseController.valueOf(context, tdlib, ForumTopicMedia.filter(index));
      page.setArguments(new SharedBaseController.Args(getChatId(), getTopicId()));
      page.setStandaloneParent(this);
      pages[index] = page;
    }
    return page;
  }

  @Override public void onMediaSelectionChanged (SharedBaseController<?> page) {
    if (isDestroyed() || activeTab < 0 || pages[activeTab] != page) {
      return;
    }
    boolean selecting = page.isInMediaSelectMode();
    selectionPage = selecting ? page : null;
    materialFrame.setPagingEnabled(!selecting);
    tabs.setTouchDisabled(selecting);
    if (selecting && selectionBar == null) {
      createSelectionBar();
    }
    if (selectionBar == null) {
      return;
    }
    selectionBar.setVisibility(selecting || selectionFactor > 0f ? View.VISIBLE : View.GONE);
    if (!selecting) {
      return;
    }
    int count = page.getSelectedMediaCount();
    selectionCounter.setSuffix(Lang.plural(page.getSelectedMediaSuffixRes(), count), false);
    selectionCounter.setCounter(count);
    boolean[] enabled = {
      page.canShareMessages(), page.canCopyMessages(), page.canDeleteMessages(),
      page.canClearMessages(), count == 1
    };
    int offset = Screen.dp(12);
    for (int i = 0; i < selectionButtons.size(); i++) {
      HeaderButton button = selectionButtons.get(i);
      button.setVisibility(enabled[i] ? View.VISIBLE : View.GONE);
      button.setEnabled(enabled[i]);
      FrameLayoutFix.LayoutParams params = (FrameLayoutFix.LayoutParams) button.getLayoutParams();
      params.gravity = Lang.rtl() ? Gravity.LEFT : Gravity.RIGHT;
      params.leftMargin = Lang.rtl() ? offset : 0;
      params.rightMargin = Lang.rtl() ? 0 : offset;
      button.setLayoutParams(params);
      if (enabled[i]) {
        offset += Screen.dp(48);
      }
    }
    FrameLayoutFix.LayoutParams counterParams =
      (FrameLayoutFix.LayoutParams) selectionCounter.getLayoutParams();
    counterParams.leftMargin = Lang.rtl() ? offset : Screen.dp(56);
    counterParams.rightMargin = Lang.rtl() ? Screen.dp(56) : offset;
    selectionCounter.setLayoutParams(counterParams);
    onMediaSelectionFactorChanged(page, selectionFactor);
  }

  private void createSelectionBar () {
    selectionBar = new FrameLayoutFix(context);
    tabWrap.addView(selectionBar, FrameLayoutFix.newParams(-1, -1));
    selectionCounter = new CounterHeaderView(context);
    selectionCounter.initDefault(16f, ColorId.text);
    selectionCounter.setTextTop(Screen.dp(30));
    addThemeInvalidateListener(selectionCounter);
    selectionBar.addView(selectionCounter, FrameLayoutFix.newParams(-1, -1));
    HeaderButton dismiss = selectionButton(R.drawable.baseline_close_24, R.string.Cancel, () -> {
      if (selectionPage != null) {
        selectionPage.setInMediaSelectMode(false);
      }
    });
    selectionBar.addView(dismiss, FrameLayoutFix.newParams(Screen.dp(56), -1,
      Lang.rtl() ? Gravity.RIGHT : Gravity.LEFT));
    int[] icons = {R.drawable.baseline_forward_24, R.drawable.baseline_content_copy_24,
      R.drawable.baseline_delete_24, R.drawable.templarian_baseline_broom_24,
      R.drawable.baseline_visibility_24};
    int[] labels = {R.string.Share, R.string.Copy, R.string.Delete, R.string.DeleteFromCache,
      R.string.ShowInChat};
    for (int i = 0; i < icons.length; i++) {
      final int action = i;
      HeaderButton button = selectionButton(icons[i], labels[i], () -> {
        SharedBaseController<?> page = selectionPage;
        if (page == null) {
          return;
        }
        switch (action) {
          case 0: page.shareMessages(); break;
          case 1: page.copyMessages(); break;
          case 2: page.deleteMessages(); break;
          case 3: page.clearMessages(); break;
          case 4: page.viewMessages(); break;
        }
      });
      selectionButtons.add(button);
      selectionBar.addView(button, FrameLayoutFix.newParams(Screen.dp(48), -1));
    }
  }

  private HeaderButton selectionButton (int icon, int label, Runnable action) {
    HeaderButton button = new HeaderButton(context);
    button.setThemeColorId(ColorId.icon);
    button.setImageResource(icon);
    button.setContentDescription(Lang.getString(label));
    button.setBackground(Theme.fillingSelector());
    addThemeInvalidateListener(button);
    button.setOnClickListener(v -> action.run());
    return button;
  }

  @Override public void onMediaSelectionFactorChanged (SharedBaseController<?> page, float factor) {
    if (activeTab < 0 || pages[activeTab] != page) {
      return;
    }
    selectionFactor = factor;
    tabs.setOverlayFactor(factor);
    if (selectionBar != null) {
      selectionBar.setTranslationY(Screen.dp(48) * (1f - factor));
      selectionBar.setVisibility(factor > 0f || page.isInMediaSelectMode() ? View.VISIBLE : View.GONE);
    }
  }

  private final class MediaPagerAdapter extends PagerAdapter {
    @Override public int getCount () {
      return visibleTabs.size();
    }

    @Override public boolean isViewFromObject (@NonNull View view, @NonNull Object object) {
      return view == ((SharedBaseController<?>) object).getValue();
    }

    @Override public int getItemPosition (@NonNull Object object) {
      for (int i = 0; i < visibleTabs.size(); i++) {
        if (pages[visibleTabs.get(i)] == object) {
          return i;
        }
      }
      return POSITION_NONE;
    }

    @NonNull @Override public Object instantiateItem (@NonNull ViewGroup container, int position) {
      int index = visibleTabs.get(position);
      SharedBaseController<?> page = mediaPage(index);
      View view = page.getValue();
      page.getRecyclerView().setFocusable(false);
      page.getRecyclerView().setFocusableInTouchMode(false);
      page.getRecyclerView().setPreserveFocusAfterLayout(false);
      page.getRecyclerView().setNestedScrollingEnabled(true);
      page.search(query);
      container.addView(view);
      if (restoredPageStates[index] != null) {
        page.getRecyclerView().getLayoutManager().onRestoreInstanceState(restoredPageStates[index]);
        restoredPageStates[index] = null;
      }
      if (index == activeTab && isFocused() && !page.isFocused()) {
        page.onFocus();
      }
      return page;
    }

    @Override public void destroyItem (@NonNull ViewGroup container, int position,
                                      @NonNull Object object) {
      SharedBaseController<?> page = (SharedBaseController<?>) object;
      container.removeView(page.getValue());
      if (page.isFocused()) {
        page.onBlur();
      }
    }
  }

  private void selectTab (int index) {
    selectTab(index, false);
  }

  private void selectTab (int index, boolean animated) {
    if (selectionPage != null) {
      return;
    }
    int position = visibleTabs.indexOf(index);
    if (position < 0) {
      activateTab(-1);
      return;
    }
    int scrollY = scroll.getScrollY();
    boolean smoothScroll = animated && !Settings.instance().needReduceMotion();
    if (smoothScroll && materialFrame.getCurrentItem() != position) {
      tabs.setFromTo(materialFrame.getCurrentItem(), position);
    }
    materialFrame.setCurrentItem(position, smoothScroll);
    activateTab(index);
    if (!smoothScroll) {
      updateSelectedTab();
    }
    Object ticket = epoch;
    scroll.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
      @Override public boolean onPreDraw () {
        scroll.getViewTreeObserver().removeOnPreDrawListener(this);
        if (!isDestroyed() && epoch == ticket && activeTab == index) {
          scroll.scrollTo(0, scrollY);
        }
        return true;
      }
    });
  }

  private void resetMediaSelection () {
    if (selectionPage != null) {
      selectionPage.setInMediaSelectMode(false);
    }
    selectionPage = null;
    selectionFactor = 0f;
    tabs.setOverlayFactor(0f);
    tabs.setTouchDisabled(false);
    materialFrame.setPagingEnabled(true);
    if (selectionBar != null) {
      selectionBar.setVisibility(View.GONE);
    }
  }

  private void activateTab (int index) {
    if (activeTab != index) {
      resetMediaSelection();
      if (activeTab >= 0 && pages[activeTab] != null) {
        pages[activeTab].setInMediaSelectMode(false);
        pages[activeTab].onBlur();
      }
      activeTab = index;
    }
    if (index >= 0) {
      SharedBaseController<?> page = mediaPage(index);
      if (page.getRecyclerView() != null) {
        page.search(query);
        if (isFocused() && !page.isFocused()) {
          page.onFocus();
        }
      }
    }
  }

  private void updateSelectedTab () {
    int index = visibleTabs.indexOf(activeTab);
    if (index >= 0 && pagerScrollState == ViewPager.SCROLL_STATE_IDLE) {
      tabs.setSelectionFactor(index);
    }
  }

  @Override public boolean canSlideBackFrom (NavigationController navigation, float x, float y) {
    if (selectionPage != null) {
      return false;
    }
    return x <= Screen.dp(12) ||
      materialFrame.getCurrentItem() == 0 && pagerScrollState == ViewPager.SCROLL_STATE_IDLE;
  }

  private void scheduleMaterialsRefresh () {
    if (refreshScheduled || !ready()) {
      return;
    }
    refreshScheduled = true;
    Object ticket = epoch;
    tdlib.ui()
      .postDelayed(
        () -> {
        if (isDestroyed() || ticket != epoch) {
          return;
        }
        refreshScheduled = false;
        refreshMaterials();
      },
        400
      );
  }

  @Override public void onNewMessage (TdApi.Message message) {
    if (message.chatId != getChatId()) {
      return;
    }
    Object ticket = epoch;
    tdlib.ui()
      .post(
        () -> {
        if (isDestroyed() || epoch != ticket || !ready()) {
          return;
        }
        rememberContentMessage(message);
        if (!ForumTopicMedia.matches(getChatId(), getTopicId(), message) ||
          !ProfileController.filterMediaMessage(message)) {
          return;
        }
        for (int i = 0; i < categories.length; i++) {
          if (Td.matchesFilter(message, ForumTopicMedia.filter(i))) {
            categories[i].arrived();
          }
        }
        renderTabs();
      }
      );
  }

  @Override public void onMessageSendSucceeded (TdApi.Message message, long oldMessageId) {
    onNewMessage(message);
  }

  @Override public void onMessageContentChanged (
    long chatId,
    long messageId,
    TdApi.MessageContent content
  ) {
    if (chatId != getChatId()) {
      return;
    }
    Object ticket = epoch;
    tdlib.ui()
      .post(
        () -> {
        if (isDestroyed() || epoch != ticket || !ready()) {
          return;
        }
        TdApi.Message cached = recentContentMessages.get(messageId);
        if (cached != null) {
          onCachedContentChanged(cached, content);
          return;
        }
        if (activeContent.containsKey(messageId)) {
          activeContent.put(messageId, content);
          return;
        }
        Long lastRequest = recentContentRequests.get(messageId);
        if (lastRequest != null && SystemClock.uptimeMillis() - lastRequest < 5000) {
          return;
        }
        if (!pendingContent.containsKey(messageId) &&
          pendingContent.size() >= MAX_PENDING_CONTENT) {
          // Overflow becomes one debounced, typed availability refresh, never a history scan.
          scheduleMaterialsRefresh();
          return;
        }
        pendingContent.put(messageId, content);
        scheduleContentPump();
      }
      );
  }

  private void rememberContentMessage (TdApi.Message message) {
    recentContentMessages.remove(message.id);
    recentContentMessages.put(message.id, message);
    if (recentContentMessages.size() > MAX_RECENT_CONTENT) {
      recentContentMessages.remove(recentContentMessages.keySet().iterator().next());
    }
  }
  /**
   * UI-thread seam: embedded pages can supply membership/metadata before replacing a cached row.
   */
  void onCachedContentChanged (TdApi.Message message, TdApi.MessageContent content) {
    if (!ready() || message.chatId != getChatId()) {
      return;
    }
    message.content = content;
    rememberContentMessage(message);
    pendingContent.remove(message.id);
    if (activeContent.containsKey(message.id)) {
      activeContent.put(message.id, content);
    }
    if (!ForumTopicMedia.matches(getChatId(), getTopicId(), message)) {
      return;
    }
    for (SharedBaseController<?> page : pages) {
      if (page != null) {
        page.editMessage(message.id, content);
        page.addMessage(message);
      }
    }
    scheduleMaterialsRefresh();
  }

  private void scheduleContentPump () {
    if (contentPumpScheduled ||
      pendingContent.isEmpty() ||
      activeContent.size() >= MAX_CONTENT_REQUESTS ||
      !ready()) {
      return;
    }
    contentPumpScheduled = true;
    Object ticket = epoch;
    tdlib.ui()
      .postDelayed(
        () -> {
        if (isDestroyed() || epoch != ticket) {
          return;
        }
        contentPumpScheduled = false;
        if (!ready()) {
          pendingContent.clear();
          return;
        }
        while (!pendingContent.isEmpty() && activeContent.size() < MAX_CONTENT_REQUESTS) {
          long messageId = pendingContent.keySet().iterator().next();
          activeContent.put(messageId, pendingContent.remove(messageId));
          recentContentRequests.remove(messageId);
          recentContentRequests.put(messageId, SystemClock.uptimeMillis());
          if (recentContentRequests.size() > MAX_RECENT_CONTENT) {
            recentContentRequests.remove(recentContentRequests.keySet().iterator().next());
          }
          tdlib.send(
            new TdApi.GetMessage(getChatId(), messageId),
            (message, error) ->
              tdlib.ui()
                .post(
                  () -> {
                if (isDestroyed() || epoch != ticket) {
                  return;
                }
                TdApi.MessageContent latest = activeContent.remove(messageId);
                if (ready() && latest != null) {
                  if (error == null && message != null) {
                    onCachedContentChanged(message, latest);
                  } else {
                    scheduleMaterialsRefresh();
                  }
                }
                scheduleContentPump();
              }
                )
          );
        }
      },
        200
      );
  }

  @Override public void onMessagesDeleted (long chatId, long[] ids) {
    if (chatId != getChatId()) {
      return;
    }
    Object ticket = epoch;
    tdlib.ui()
      .post(
        () -> {
        if (isDestroyed() || epoch != ticket) {
          return;
        }
        for (long id : ids) {
          recentContentMessages.remove(id);
          pendingContent.remove(id);
          // Retain the slot until its callback arrives, but never resurrect a deleted row.
          if (activeContent.containsKey(id)) {
            activeContent.put(id, null);
          }
        }
        scheduleMaterialsRefresh();
      }
      );
  }

  @Override public void onChatTitleChanged (long chatId, String title) {
    runOnUiThreadOptional(this::render);
  }

  @Override public void onChatPermissionsChanged (long chatId, TdApi.ChatPermissions permissions) {
    runOnUiThreadOptional(this::render);
  }

  @Override public void onSupergroupUpdated (TdApi.Supergroup group) {
    runOnUiThreadOptional(this::render);
  }

  @Override public void onNotificationSettingsChanged (
    long chatId,
    TdApi.ChatNotificationSettings settings
  ) {
    if (chatId == getChatId()) {
      runOnUiThreadOptional(this::render);
    }
  }

  @Override public void onNotificationSettingsChanged (
    TdApi.NotificationSettingsScope scope,
    TdApi.ScopeNotificationSettings settings
  ) {
    if (Td.matchesScope(tdlib.chatType(getChatId()), scope)) {
      runOnUiThreadOptional(this::render);
    }
  }

  @Override public void onFocus () {
    super.onFocus();
    render();
    updateHeader(scroll.getScrollY());
    if (activeTab >= 0 && pages[activeTab] != null && !pages[activeTab].isFocused()) {
      pages[activeTab].onFocus();
    }
    // region @MadeByLLM("gpt-6-astra", id = "forum-topics")
    if (returnToTopics) {
      returnToTopics(8);
    }
    // endregion
    if (restoreSearch) {
      restoreSearch = false;
      String saved = query;
      openSearchMode();
      setSearchInput(saved);
    }
  }

  @Override public void onBlur () {
    if (activeTab >= 0 && pages[activeTab] != null) {
      pages[activeTab].onBlur();
    }
    super.onBlur();
  }

  @Override protected void handleLanguagePackEvent (int event, int arg1) {
    super.handleLanguagePackEvent(event, arg1);
    visibleTabs = Collections.emptyList();
    render();
  }

  public static void saveArguments (Bundle out, String prefix, Arguments args) {
    out.putLong(prefix + "forum_profile_chat", args.chatId);
    out.putInt(prefix + "forum_profile_topic", args.forumTopicId);
    out.putString(
      prefix + "forum_profile_list",
      args.chatList != null ?
        TD.makeChatListKey(args.chatList) : ""
    );
  }

  @Nullable public static Arguments restoreArguments (Bundle in, String prefix) {
    long chatId = in.getLong(prefix + "forum_profile_chat");
    int topicId = in.getInt(prefix + "forum_profile_topic");
    return chatId != 0 &&
      topicId > 0 ?
        new Arguments(
          chatId,
          topicId,
          TD.chatListFromKey(in.getString(prefix + "forum_profile_list"))
        ) : null;
  }

  @Override public boolean saveInstanceState (Bundle out, String prefix) {
    super.saveInstanceState(out, prefix);
    saveArguments(out, prefix, getArgumentsStrict());
    out.putInt(prefix + "forum_profile_tab", activeTab);
    out.putInt(
      prefix + "forum_profile_scroll",
      scroll != null &&
        !restoreScrollPending ? scroll.getScrollY() : restoreScroll
    );
    out.putString(prefix + "forum_profile_query", query);
    for (int i = 0; i < pages.length; i++) {
      Parcelable state = pages[i] != null &&
        pages[i].getRecyclerView() != null ?
          pages[i].getRecyclerView().getLayoutManager().onSaveInstanceState() :
            restoredPageStates[i];
      if (state != null) {
        out.putParcelable(prefix + "forum_profile_page_" + i, state);
      }
    }
    return true;
  }

  @Override public boolean restoreInstanceState (Bundle in, String prefix) {
    Arguments args = restoreArguments(in, prefix);
    if (args == null || tdlib.chatSync(args.chatId) == null || !tdlib.isForum(args.chatId)) {
      return false;
    }
    setArguments(args);
    restoreTab = in.getInt(prefix + "forum_profile_tab", -1);
    restoreScroll = in.getInt(prefix + "forum_profile_scroll", 0);
    restoreScrollPending = restoreScroll > 0;
    query = in.getString(prefix + "forum_profile_query", "");
    restoreSearch = !query.isEmpty();
    for (int i = 0; i < pages.length; i++) {
      restoredPageStates[i] = in.getParcelable(prefix + "forum_profile_page_" + i);
    }
    super.restoreInstanceState(in, prefix);
    return true;
  }

  @Override public void onPerformUserCleanup () {
    epoch = new Object();
    // region @MadeByLLM("gpt-6-astra", id = "forum-topics")
    returnToTopics = false;
    // endregion
    tdlib.ui()
      .post(
        () -> {
        if (isDestroyed()) {
          return;
        }
        if (subscription != null) {
          subscription.close();
          subscription = null;
        }
        profileLink.invalidate();
        clearContentUpdates();
        for (ForumTopicMedia.Category category : categories) {
          category.invalidate();
        }
        materialFrame.setAdapter(null);
        for (int i = 0; i < pages.length; i++) {
          if (pages[i] != null) {
            pages[i].destroy();
            pages[i] = null;
          }
        }
        topic = null;
        terminal = true;
        busy = false;
        render();
      }
      );
  }

  @Override public void onPerformRestart () {
    onPerformUserCleanup();
  }

  @Override public void onPerformStartup (boolean afterRestart) {
    /* Re-open explicitly after an account lifecycle boundary. */
  }

  private void clearContentUpdates () {
    recentContentMessages.clear();
    recentContentRequests.clear();
    pendingContent.clear();
    activeContent.clear();
    contentPumpScheduled = false;
  }

  @Override public void destroy () {
    epoch = new Object();
    if (subscription != null) {
      subscription.close();
    }
    profileLink.invalidate();
    clearContentUpdates();
    if (materialFrame != null) {
      materialFrame.setAdapter(null);
    }
    for (SharedBaseController<?> page : pages) {
      if (page != null) {
        page.destroy();
      }
    }
    if (headerCell != null) {
      headerCell.performDestroy();
    }
    if (tabs != null) {
      tabs.resetFromTo();
      tabs.performDestroy();
    }
    for (SettingView view : new SettingView[] {group, notifications, linkRow}) {
      if (view != null) {
        view.performDestroy();
      }
    }
    if (subscribed) {
      tdlib.listeners().unsubscribeFromChatUpdates(getChatId(), this);
      tdlib.listeners().unsubscribeFromMessageUpdates(getChatId(), this);
      tdlib.listeners().unsubscribeFromSettingsUpdates(this);
      tdlib.listeners().removeCleanupListener(this);
      tdlib.cache().unsubscribeFromSupergroupUpdates(ChatId.toSupergroupId(getChatId()), this);
    }
    super.destroy();
  }
}