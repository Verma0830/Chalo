package com.chalo.customer.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * JUnit rule that replaces Dispatchers.Main with a test dispatcher for the duration of each test.
 * This makes ViewModels that use viewModelScope fully controllable in unit tests.
 *
 * Usage:
 *   @get:Rule val mainDispatcherRule = MainDispatcherRule()
 *
 *   Then use mainDispatcherRule.testDispatcher.scheduler.advanceUntilIdle() to run coroutines,
 *   or use runTest { } which uses the same dispatcher automatically.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    val testDispatcher: TestDispatcher = StandardTestDispatcher(),
) : TestWatcher() {

    override fun starting(description: Description) {
        Dispatchers.setMain(testDispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}
