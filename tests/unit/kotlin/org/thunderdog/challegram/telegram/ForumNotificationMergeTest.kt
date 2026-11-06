package org.thunderdog.challegram.telegram

import com.google.common.truth.Truth.assertThat
import me.vkryl.annotation.MadeByLLM
import org.drinkless.tdlib.TdApi
import org.junit.Test

@MadeByLLM(value = "GPT-6", id = "pr-1110-fork-integration")
class ForumNotificationMergeTest {
  private fun notification(
    topic: Int,
    forwarded: Boolean,
    chatId: Long = -100,
    senderId: Long = 7
  ): TdlibNotification {
    val message = TdApi.Message().apply {
      this.chatId = chatId
      this.senderId = TdApi.MessageSenderUser(senderId)
      topicId = TdApi.MessageTopicForum(topic)
      date = 123
      if (forwarded) {
        forwardInfo = TdApi.MessageForwardInfo()
      } else {
        mediaAlbumId = 456
      }
    }
    val value = TdApi.Notification().apply {
      type = TdApi.NotificationTypeNewMessage(message, true)
    }
    val result = TdlibNotification(1)
    // Exercise grouping without initializing an account or native TDLib.
    TdlibNotification::class.java.getDeclaredField("notification").apply {
      isAccessible = true
      set(result, value)
    }
    return result
  }

  @Test
  fun differentTopicsNeverMergeForwardedMessages() {
    assertThat(notification(7, true).canMergeWith(notification(9, true))).isFalse()
  }

  @Test
  fun differentTopicsNeverMergeAlbums() {
    assertThat(notification(7, false).canMergeWith(notification(9, false))).isFalse()
  }

  @Test
  fun sameTopicRetainsAlbumAndForwardGrouping() {
    assertThat(notification(7, true).canMergeWith(notification(7, true))).isTrue()
    assertThat(notification(7, false).canMergeWith(notification(7, false))).isTrue()
  }

  @Test
  fun differentChatsNeverMergeForwardedMessages() {
    assertThat(notification(7, true).canMergeWith(notification(7, true, chatId = -200)))
      .isFalse()
  }

  @Test
  fun differentSendersNeverMergeForwardedMessages() {
    assertThat(notification(7, true).canMergeWith(notification(7, true, senderId = 8)))
      .isFalse()
  }
}
