package io.github.togls.hypertweaks.feature.googlephotos.xposed

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class GooglePhotosMapSessionStateMachineTest {

    @Test
    fun homeActivityResumedAndMapAttachedActivatesSession() {
        val stateMachine = stateMachine()
        val activity = ActivityKey()
        stateMachine.onActivityCreated(activity, GooglePhotosClassNames.HomeActivity)
        stateMachine.onActivityResumed(activity, GooglePhotosClassNames.HomeActivity)

        val transition = stateMachine.onMapViewAttached(ViewKey(), activity)

        assertTrue(transition.activatedSession != null)
        assertSame(activity, transition.activeSession?.hostActivity)
    }

    @Test
    fun collectionsActivityResumedAndMapAttachedActivatesSession() {
        val stateMachine = stateMachine()
        val activity = ActivityKey()
        stateMachine.onActivityCreated(activity, GooglePhotosClassNames.CollectionsActivity)
        stateMachine.onActivityResumed(activity, GooglePhotosClassNames.CollectionsActivity)

        val transition = stateMachine.onMapViewAttached(ViewKey(), activity)

        assertNotNull(transition.activatedSession)
    }

    @Test
    fun mapExploreActivityIsRejectedAsSinglePhotoMap() {
        val stateMachine = stateMachine()
        val activity = ActivityKey()
        stateMachine.onActivityCreated(activity, GooglePhotosClassNames.MapExploreActivity)
        stateMachine.onActivityResumed(activity, GooglePhotosClassNames.MapExploreActivity)

        val transition = stateMachine.onMapViewAttached(ViewKey(), activity)

        assertEquals(MapSessionRejectionReason.SINGLE_PHOTO_MAP, transition.reason)
        assertNull(stateMachine.currentSession())
    }

    @Test
    fun unknownActivityIsRejected() {
        val stateMachine = stateMachine()
        val activity = ActivityKey()
        stateMachine.onActivityCreated(activity, "example.UnknownActivity")
        stateMachine.onActivityResumed(activity, "example.UnknownActivity")

        val transition = stateMachine.onMapViewAttached(ViewKey(), activity)

        assertEquals(MapSessionRejectionReason.UNKNOWN_HOST, transition.reason)
        assertNull(stateMachine.currentSession())
    }

    @Test
    fun attachedMapActivatesAfterActivityResumes() {
        val stateMachine = stateMachine()
        val activity = ActivityKey()
        stateMachine.onActivityCreated(activity, GooglePhotosClassNames.HomeActivity)
        val attachTransition = stateMachine.onMapViewAttached(ViewKey(), activity)

        val resumeTransition = stateMachine.onActivityResumed(activity, GooglePhotosClassNames.HomeActivity)

        assertEquals(MapSessionRejectionReason.HOST_NOT_RESUMED, attachTransition.reason)
        assertNotNull(resumeTransition.activatedSession)
    }

    @Test
    fun resumedActivityActivatesAfterMapAttaches() {
        val stateMachine = stateMachine()
        val activity = ActivityKey()
        stateMachine.onActivityCreated(activity, GooglePhotosClassNames.HomeActivity)

        val resumeTransition = stateMachine.onActivityResumed(activity, GooglePhotosClassNames.HomeActivity)
        val attachTransition = stateMachine.onMapViewAttached(ViewKey(), activity)

        assertEquals(MapSessionRejectionReason.MAP_VIEW_NOT_ATTACHED, resumeTransition.reason)
        assertNotNull(attachTransition.activatedSession)
    }

    @Test
    fun mapDetachDeactivatesSession() {
        val stateMachine = stateMachine()
        val activity = activate(stateMachine)
        val view = attachedView

        val transition = stateMachine.onMapViewDetached(view)

        assertEquals(MapSessionRejectionReason.VIEW_DETACHED, transition.reason)
        assertNotNull(transition.deactivatedSession)
        assertNull(stateMachine.currentSession())
        assertSame(activity, transition.deactivatedSession?.hostActivity)
    }

    @Test
    fun activityPauseDeactivatesSession() {
        val stateMachine = stateMachine()
        val activity = activate(stateMachine)

        val transition = stateMachine.onActivityPaused(activity)

        assertEquals(MapSessionRejectionReason.ACTIVITY_PAUSED, transition.reason)
        assertNotNull(transition.deactivatedSession)
        assertNull(stateMachine.currentSession())
    }

    @Test
    fun activityDestroyCleansViewsAndSession() {
        val stateMachine = stateMachine()
        val activity = activate(stateMachine)

        val transition = stateMachine.onActivityDestroyed(activity)
        val detachedTransition = stateMachine.onMapViewDetached(attachedView)

        assertEquals(MapSessionRejectionReason.ACTIVITY_DESTROYED, transition.reason)
        assertNull(stateMachine.currentSession())
        assertEquals(MapSessionRejectionReason.VIEW_DETACHED, detachedTransition.reason)
    }

    @Test
    fun repeatedAttachDoesNotCreateAnotherSession() {
        val stateMachine = stateMachine()
        val activity = ActivityKey()
        val view = ViewKey()
        stateMachine.onActivityCreated(activity, GooglePhotosClassNames.HomeActivity)
        stateMachine.onActivityResumed(activity, GooglePhotosClassNames.HomeActivity)

        val first = stateMachine.onMapViewAttached(view, activity)
        val repeated = stateMachine.onMapViewAttached(view, activity)

        assertNotNull(first.activatedSession)
        assertNull(repeated.activatedSession)
        assertEquals(first.activeSession?.sessionId, repeated.activeSession?.sessionId)
    }

    @Test
    fun detachingOneOfMultipleViewsKeepsSessionActive() {
        val stateMachine = stateMachine()
        val activity = ActivityKey()
        val firstView = ViewKey()
        val secondView = ViewKey()
        stateMachine.onActivityCreated(activity, GooglePhotosClassNames.HomeActivity)
        stateMachine.onActivityResumed(activity, GooglePhotosClassNames.HomeActivity)
        stateMachine.onMapViewAttached(firstView, activity)
        stateMachine.onMapViewAttached(secondView, activity)

        val transition = stateMachine.onMapViewDetached(firstView)

        assertNull(transition.deactivatedSession)
        assertNotNull(stateMachine.currentSession())
        assertEquals(1, transition.attachedViewCount)
    }

    @Test
    fun viewWithoutHostActivityIsRejected() {
        val transition = stateMachine().onMapViewAttached(ViewKey(), null)

        assertEquals(MapSessionRejectionReason.NO_HOST_ACTIVITY, transition.reason)
        assertNull(transition.activeSession)
    }

    @Test
    fun resumingDifferentActivityFailsClosedUntilItsMapAttaches() {
        val stateMachine = stateMachine()
        activate(stateMachine)
        val nextActivity = ActivityKey()
        stateMachine.onActivityCreated(nextActivity, GooglePhotosClassNames.CollectionsActivity)

        val transition = stateMachine.onActivityResumed(
            nextActivity,
            GooglePhotosClassNames.CollectionsActivity,
        )

        assertNotNull(transition.deactivatedSession)
        assertEquals(MapSessionRejectionReason.MAP_VIEW_NOT_ATTACHED, transition.reason)
        assertNull(stateMachine.currentSession())
    }

    private lateinit var attachedView: ViewKey

    private fun activate(
        stateMachine: GooglePhotosMapSessionStateMachine<ActivityKey, ViewKey>,
    ): ActivityKey {
        val activity = ActivityKey()
        attachedView = ViewKey()
        stateMachine.onActivityCreated(activity, GooglePhotosClassNames.HomeActivity)
        stateMachine.onActivityResumed(activity, GooglePhotosClassNames.HomeActivity)
        stateMachine.onMapViewAttached(attachedView, activity)
        return activity
    }

    private fun stateMachine() = GooglePhotosMapSessionStateMachine<ActivityKey, ViewKey>()

    private class ActivityKey
    private class ViewKey
}
