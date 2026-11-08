# Topicsgram topic correction review — 2026-11-08

Reviewed `iMiKED/TopicsGram-X` main at
[`15c1e0c14df4dcd16fee2f6d97a25768f48f3da0`](https://github.com/iMiKED/TopicsGram-X/commit/15c1e0c14df4dcd16fee2f6d97a25768f48f3da0)
against the integrated clean topic base `361a0adaafa48153dfe52fab3f05097685d2cb03`
and TGX-Next `ee7ea422`. Target: latest, Android 13+, ARM64.

## Selected commits

- [`614d6e18`](https://github.com/iMiKED/TopicsGram-X/commit/614d6e18fbcedd2a5db46aef7dfc9efce9265778):
  configure advance-width measurement before TextView caches topic labels on API 35+.
  Keep the API guard since TGX-Next supports Android 13 and 14 too. Restore the required
  `android.os.Build` import, which TGX-Next had removed with obsolete legacy checks.
- [`f803cc0c`](https://github.com/iMiKED/TopicsGram-X/commit/f803cc0cd553954bbb67e7dbbb149d9cb32f2d1b):
  update visible rows through the upstream refresh helper, defer refreshes until structural
  RecyclerView updates finish, and refresh relative dates/time formatting through DateManager.
  Review confirmed detached views retain pending work, observers are removed on destruction,
  stable item IDs are checked where layout positions are used, and offscreen rows are invalidated
  by Views.updateRows. The editor's focused form is excluded from icon row refreshes.
  Resolve conflicts preserving TGX-Next's EditHeaderView, ViewPagerTopView sections, their cleanup,
  MoreDelegate popup menus and removal of manual refresh.
- [`0fa69fa4`](https://github.com/iMiKED/TopicsGram-X/commit/0fa69fa433c360d181a2e3470852182a6a1d8547):
  skip the normal-path layout request when the forum container has no session, rail or topic views.
  Preserve activation, cleanup and existing transition branches.

Cherry-picks retain the source author and `-x` provenance.

## Already present or excluded

The imported clean base already contains the current Java ForumTopicStore, ForumUnreadCounter,
ForumTopicActions, ForumTopicContext and notification read-scope fixes. Comparing their files to
Topicsgram main showed no additional changes. The older Kotlin pagination correction
`0f704cc2`/`ac5f556c` is already represented in that Java implementation; replaying it would
reintroduce obsolete files.

`46a3c5f5` switches selection animation APIs to animatorx without changing its behavior. The
existing FactorAnimator remains supported in TGX-Next; this migration is not a needed bug fix.
The remaining forum draft codec delta supports Topicsgram's binary article snapshots and changes
the persistence format. It is not required by TGX-Next's topic drafts. Legacy API 16–20 fixes,
article features, product localization and unrelated upstream merges were excluded.

Topicsgram pins TDLib `84d3ba922912382000d50e2771a1d9b7e4d14066`. That commit is already
an ancestor of TGX-Next's `40d51146716dfe79b00c510b6b360f74c8c72378`, so no native
TDLib update is needed to include its changes.

The requested TGX-Next conversation geometry, notification scope, simplified menus, tab gestures
and chat rail design are preserved.

## Validation

- 21 extracted actual-method refresh scenarios passed in a JVM harness. Covers ready/no-op,
  destroyed/detached/reattached states, computing-layout, pending structural updates, requested
  layouts, deduplicated pre-draw registration and single delivery after the layout settles.
  This harness uses simulated Android components; it does not validate RecyclerView runtime,
  typography allocations, frame performance or device lifecycle behavior.
- The first release compile caught the missing Build import; fixed before the final build.
- Final `assembleLatestRelease`, `lintLatestRelease` and `checkMinSdk` passed with
  `-Pabi=arm64 --max-workers=2`; log: `/tmp/topicsgram-fixes-release.log`.
- No live-device flow, date/locale switching or typography allocation benchmark was run.

Temporary harness: `tests/unit/checks/topicsgram-refresh/check.py` (ignored).
No live account settings or messages were changed for this review.
