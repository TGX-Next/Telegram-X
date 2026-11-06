package org.thunderdog.challegram.util

import com.google.common.truth.Truth.assertThat
import org.junit.Test

// @MadeByLLM("gpt-6")
class AppUpdaterTest {
  @Test
  fun requestDuringCheckRunsAgainBeforePublishingOldResult() {
    val updater = TestUpdater()
    updater.checkForUpdates()
    updater.checkForUpdates()

    assertThat(updater.checks).isEqualTo(1)
    updater.completeCheck()
    assertThat(updater.checks).isEqualTo(2)
    assertThat(updater.state()).isEqualTo(AppUpdater.State.CHECKING)
    assertThat(updater.publishedResults).isEqualTo(0)

    updater.completeCheck()
    assertThat(updater.state()).isEqualTo(AppUpdater.State.NONE)
    assertThat(updater.publishedResults).isEqualTo(1)
  }

  @Test
  fun repeatedRequestsAreCoalescedIntoOneFollowUp() {
    val updater = TestUpdater()
    repeat(5) {
      updater.checkForUpdates()
    }
    updater.completeCheck()
    updater.completeCheck()
    assertThat(updater.checks).isEqualTo(2)
    assertThat(updater.publishedResults).isEqualTo(1)
  }

  @Test
  fun completedCheckCanBeRequestedAgain() {
    val updater = TestUpdater()
    updater.checkForUpdates()
    updater.completeCheck()
    updater.checkForUpdates()
    assertThat(updater.checks).isEqualTo(2)
    assertThat(updater.state()).isEqualTo(AppUpdater.State.CHECKING)
  }

  @Test
  fun checkingDoesNotInterruptDownload() {
    val updater = TestUpdater()
    updater.setState(AppUpdater.State.DOWNLOADING)
    updater.checkForUpdates()
    assertThat(updater.checks).isEqualTo(0)
    assertThat(updater.state()).isEqualTo(AppUpdater.State.DOWNLOADING)
  }

  private class TestUpdater : AppUpdater(null) {
    var checks = 0
    var publishedResults = 0

    override fun checkForTelegramChannelUpdates() {
      checks++
      setState(State.CHECKING)
    }

    fun completeCheck() {
      if (!restartPendingUpdateCheck()) {
        publishedResults++
        setState(State.NONE)
      }
    }
  }
}
