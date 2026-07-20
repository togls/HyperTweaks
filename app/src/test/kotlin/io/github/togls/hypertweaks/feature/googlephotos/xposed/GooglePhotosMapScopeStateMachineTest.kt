package io.github.togls.hypertweaks.feature.googlephotos.xposed

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class GooglePhotosMapScopeStateMachineTest {

    @Test
    fun onlyCollectionsScopeCanBecomeActive() {
        val stateMachine = GooglePhotosMapScopeStateMachine<ScopeKey>()
        val collectionsActivity = ScopeKey()
        val otherActivity = ScopeKey()
        stateMachine.bind(collectionsActivity, scope(MapEntrySource.COLLECTIONS))
        stateMachine.bind(otherActivity, scope(MapEntrySource.OTHER))

        assertTrue(stateMachine.activate(collectionsActivity))
        assertSame(collectionsActivity, stateMachine.currentCollectionsActivity())
        assertFalse(stateMachine.activate(otherActivity))
        assertNull(stateMachine.currentCollectionsActivity())
    }

    @Test
    fun pauseImmediatelyClearsActiveScope() {
        val stateMachine = GooglePhotosMapScopeStateMachine<ScopeKey>()
        val activity = ScopeKey()
        stateMachine.bind(activity, scope(MapEntrySource.COLLECTIONS))
        stateMachine.activate(activity)

        assertTrue(stateMachine.deactivate(activity))
        assertNull(stateMachine.currentCollectionsActivity())
        assertFalse(stateMachine.deactivate(activity))
    }

    @Test
    fun boundCollectionsScopeRemainsAvailableForBackgroundIndexBuild() {
        val stateMachine = GooglePhotosMapScopeStateMachine<ScopeKey>()
        val activity = ScopeKey()

        stateMachine.bind(activity, scope(MapEntrySource.COLLECTIONS))

        assertTrue(stateMachine.hasCollectionsScope())
        stateMachine.remove(activity)
        assertFalse(stateMachine.hasCollectionsScope())
    }

    @Test
    fun otherScopeDoesNotEnableBackgroundIndexBuild() {
        val stateMachine = GooglePhotosMapScopeStateMachine<ScopeKey>()

        stateMachine.bind(ScopeKey(), scope(MapEntrySource.OTHER))

        assertFalse(stateMachine.hasCollectionsScope())
    }

    @Test
    fun destroyRemovesScopeAndPreventsReactivation() {
        val stateMachine = GooglePhotosMapScopeStateMachine<ScopeKey>()
        val activity = ScopeKey()
        stateMachine.bind(activity, scope(MapEntrySource.COLLECTIONS))
        stateMachine.activate(activity)

        assertTrue(stateMachine.remove(activity))
        assertNull(stateMachine.currentCollectionsActivity())
        assertFalse(stateMachine.activate(activity))
    }

    @Test
    fun recreatedActivityDoesNotInheritPreviousInstanceScope() {
        val stateMachine = GooglePhotosMapScopeStateMachine<ScopeKey>()
        val previousActivity = ScopeKey()
        val recreatedActivity = ScopeKey()
        stateMachine.bind(previousActivity, scope(MapEntrySource.COLLECTIONS))

        assertFalse(stateMachine.activate(recreatedActivity))
        assertNull(stateMachine.currentCollectionsActivity())
    }

    private fun scope(source: MapEntrySource): MapActivityScope {
        return MapActivityScope(
            source = source,
            createdAtElapsedRealtime = 100L,
        )
    }

    private class ScopeKey
}
