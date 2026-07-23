package io.github.togls.hypertweaks.feature.googlephotos.xposed

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class GooglePhotosMapSessionStateMachineTest {

    @Test
    fun mapExploreActivityActivatesSessionDuringCreate() {
        val stateMachine = stateMachine()
        val activity = ActivityKey()

        val transition = stateMachine.onActivityCreated(
            activity,
            GooglePhotosClassNames.MapExploreActivity,
        )

        assertNotNull(transition.activatedSession)
        assertNotNull(transition.activeSession)
        assertSame(
            activity,
            transition.activeSession?.hostActivity,
        )
        assertEquals(
            GooglePhotosClassNames.MapExploreActivity,
            transition.activeSession?.hostClassName,
        )
        assertEquals(
            transition.activatedSession?.sessionId,
            transition.activeSession?.sessionId,
        )
        assertSame(
            transition.activeSession,
            stateMachine.currentSession(),
        )
    }

    @Test
    fun mapExploreResumeKeepsSessionCreatedDuringCreate() {
        val stateMachine = stateMachine()
        val activity = ActivityKey()

        val created = stateMachine.onActivityCreated(
            activity,
            GooglePhotosClassNames.MapExploreActivity,
        )

        val resumed = stateMachine.onActivityResumed(
            activity,
            GooglePhotosClassNames.MapExploreActivity,
        )

        assertNotNull(created.activatedSession)

        // onResume 不能重复创建新的 Session。
        assertNull(resumed.activatedSession)
        assertNull(resumed.deactivatedSession)

        assertEquals(
            created.activeSession?.sessionId,
            resumed.activeSession?.sessionId,
        )
        assertSame(
            activity,
            resumed.currentResumedActivity,
        )
    }

    @Test
    fun mapExploreDoesNotRequireMapViewAttachment() {
        val stateMachine = stateMachine()
        val activity = ActivityKey()

        val transition = stateMachine.onActivityCreated(
            activity,
            GooglePhotosClassNames.MapExploreActivity,
        )

        assertNotNull(transition.activeSession)

        // 字段暂时保留用于兼容现有日志，但不再参与作用域判断。
        assertEquals(
            0,
            transition.attachedViewCount,
        )
    }

    @Test
    fun homeActivityDoesNotActivateSession() {
        val stateMachine = stateMachine()
        val activity = ActivityKey()

        val transition = stateMachine.onActivityCreated(
            activity,
            GooglePhotosClassNames.HomeActivity,
        )

        assertNull(transition.activeSession)
        assertNull(transition.activatedSession)
        assertEquals(
            MapSessionRejectionReason.NOT_MAP_EXPLORE,
            transition.reason,
        )
        assertNull(stateMachine.currentSession())
    }

    @Test
    fun collectionsActivityDoesNotActivateSession() {
        val stateMachine = stateMachine()
        val activity = ActivityKey()

        val transition = stateMachine.onActivityCreated(
            activity,
            GooglePhotosClassNames.CollectionsActivity,
        )

        assertNull(transition.activeSession)
        assertEquals(
            MapSessionRejectionReason.NOT_MAP_EXPLORE,
            transition.reason,
        )
    }

    @Test
    fun unknownActivityDoesNotActivateSession() {
        val stateMachine = stateMachine()
        val activity = ActivityKey()

        val transition = stateMachine.onActivityCreated(
            activity,
            "example.UnknownActivity",
        )

        assertNull(transition.activeSession)
        assertEquals(
            MapSessionRejectionReason.NOT_MAP_EXPLORE,
            transition.reason,
        )
    }

    @Test
    fun pausingMapExploreDeactivatesSession() {
        val stateMachine = stateMachine()
        val activity = activateMapExplore(stateMachine)

        val transition = stateMachine.onActivityPaused(activity)

        assertNull(transition.activeSession)
        assertNotNull(transition.deactivatedSession)
        assertSame(
            activity,
            transition.deactivatedSession?.hostActivity,
        )
        assertEquals(
            MapSessionRejectionReason.ACTIVITY_PAUSED,
            transition.reason,
        )
        assertNull(stateMachine.currentSession())
    }

    @Test
    fun destroyingMapExploreDeactivatesSession() {
        val stateMachine = stateMachine()
        val activity = activateMapExplore(stateMachine)

        val transition = stateMachine.onActivityDestroyed(activity)

        assertNull(transition.activeSession)
        assertNotNull(transition.deactivatedSession)
        assertSame(
            activity,
            transition.deactivatedSession?.hostActivity,
        )
        assertEquals(
            MapSessionRejectionReason.ACTIVITY_DESTROYED,
            transition.reason,
        )
        assertNull(stateMachine.currentSession())
    }

    @Test
    fun destroyingInactiveActivityDoesNotDeactivateMapSession() {
        val stateMachine = stateMachine()
        val mapActivity = activateMapExplore(stateMachine)
        val unrelatedActivity = ActivityKey()

        stateMachine.onActivityCreated(
            unrelatedActivity,
            GooglePhotosClassNames.HomeActivity,
        )

        val transition =
            stateMachine.onActivityDestroyed(unrelatedActivity)

        assertNull(transition.deactivatedSession)
        assertSame(
            mapActivity,
            stateMachine.currentSession()?.hostActivity,
        )
    }

    @Test
    fun resumingSameMapExploreDoesNotCreateAnotherSession() {
        val stateMachine = stateMachine()
        val activity = ActivityKey()

        val created = stateMachine.onActivityCreated(
            activity,
            GooglePhotosClassNames.MapExploreActivity,
        )

        val firstSessionId =
            created.activeSession?.sessionId

        repeat(3) {
            val transition = stateMachine.onActivityResumed(
                activity,
                GooglePhotosClassNames.MapExploreActivity,
            )

            assertNull(transition.activatedSession)
            assertEquals(
                firstSessionId,
                transition.activeSession?.sessionId,
            )
        }
    }

    @Test
    fun creatingSecondMapExploreReplacesPreviousSession() {
        val stateMachine = stateMachine()
        val firstActivity = ActivityKey()
        val secondActivity = ActivityKey()

        val first = stateMachine.onActivityCreated(
            firstActivity,
            GooglePhotosClassNames.MapExploreActivity,
        )

        val second = stateMachine.onActivityCreated(
            secondActivity,
            GooglePhotosClassNames.MapExploreActivity,
        )

        assertNotNull(first.activatedSession)
        assertNotNull(second.activatedSession)
        assertNotNull(second.deactivatedSession)

        assertSame(
            firstActivity,
            second.deactivatedSession?.hostActivity,
        )
        assertSame(
            secondActivity,
            second.activeSession?.hostActivity,
        )

        assertEquals(
            first.activeSession!!.sessionId + 1L,
            second.activeSession!!.sessionId,
        )
    }

    @Test
    fun resumingNonMapActivityClearsStaleMapSession() {
        val stateMachine = stateMachine()
        val mapActivity = activateMapExplore(stateMachine)
        val homeActivity = ActivityKey()

        stateMachine.onActivityCreated(
            homeActivity,
            GooglePhotosClassNames.HomeActivity,
        )

        val transition = stateMachine.onActivityResumed(
            homeActivity,
            GooglePhotosClassNames.HomeActivity,
        )

        assertNull(transition.activeSession)
        assertNotNull(transition.deactivatedSession)
        assertSame(
            mapActivity,
            transition.deactivatedSession?.hostActivity,
        )
        assertEquals(
            MapSessionRejectionReason.NOT_MAP_EXPLORE,
            transition.reason,
        )
        assertNull(stateMachine.currentSession())
    }

    @Test
    fun currentResumedActivityIsClearedOnPause() {
        val stateMachine = stateMachine()
        val activity = ActivityKey()

        stateMachine.onActivityCreated(
            activity,
            GooglePhotosClassNames.MapExploreActivity,
        )

        stateMachine.onActivityResumed(
            activity,
            GooglePhotosClassNames.MapExploreActivity,
        )

        assertSame(
            activity,
            stateMachine.currentResumedActivity(),
        )

        val paused =
            stateMachine.onActivityPaused(activity)

        assertNull(paused.currentResumedActivity)
        assertNull(stateMachine.currentResumedActivity())
    }

    private fun activateMapExplore(
        stateMachine: GooglePhotosMapSessionStateMachine<ActivityKey>,
    ): ActivityKey {
        val activity = ActivityKey()

        stateMachine.onActivityCreated(
            activity,
            GooglePhotosClassNames.MapExploreActivity,
        )

        return activity
    }

    private fun stateMachine() =
        GooglePhotosMapSessionStateMachine<ActivityKey>()

    private class ActivityKey
}
