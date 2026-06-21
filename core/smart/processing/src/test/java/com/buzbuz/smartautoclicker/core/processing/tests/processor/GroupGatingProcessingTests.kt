/*
 * Copyright (C) 2026 Nicolas Espinoza
 */
package com.buzbuz.smartautoclicker.core.processing.tests.processor

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.buzbuz.smartautoclicker.core.common.actions.AndroidActionExecutor
import com.buzbuz.smartautoclicker.core.detection.ImageDetector
import com.buzbuz.smartautoclicker.core.domain.model.AND
import com.buzbuz.smartautoclicker.core.domain.model.event.EventGroup
import com.buzbuz.smartautoclicker.core.domain.model.event.GroupEventType
import com.buzbuz.smartautoclicker.core.processing.data.processor.ScenarioProcessor
import com.buzbuz.smartautoclicker.core.processing.data.scaling.ScalingManager
import com.buzbuz.smartautoclicker.core.processing.domain.SmartProcessingListener
import com.buzbuz.smartautoclicker.core.processing.utils.anyNotNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.doAnswer
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.Q])
class GroupGatingProcessingTests {

    private val testsData: ProcessingTestData = ProcessingTestData

    @Mock private lateinit var mockScalingManager: ScalingManager
    @Mock private lateinit var mockBitmapSupplier: ProcessingTests.BitmapSupplier
    @Mock private lateinit var mockImageDetector: ImageDetector
    @Mock private lateinit var mockAndroidExecutor: AndroidActionExecutor
    @Mock private lateinit var mockProcessingListener: SmartProcessingListener

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        Dispatchers.setMain(StandardTestDispatcher())

        `when`(mockScalingManager.scaleUpDetectionResult(anyNotNull()))
            .doAnswer { invocation -> invocation.getArgument(0) }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        testsData.reset()
    }

    @Test
    fun `Events in inactive group are skipped`() = runTest {
        val scenarioId = testsData.newScenarioId()
        val groupId = testsData.newEventGroupId()
        val eventId = testsData.newEventId()
        val gateCondition = testsData.newTestImageCondition(groupId, shouldBeDetected = true)
        val eventCondition = testsData.newTestImageCondition(eventId, shouldBeDetected = true)

        val group = EventGroup(
            id = groupId,
            scenarioId = scenarioId,
            name = "Gated group",
            eventType = GroupEventType.IMAGE,
            conditionOperator = AND,
            priority = 0,
            conditions = listOf(gateCondition.imageCondition),
        )

        val event = testsData.newTestImageEvent(
            eventId = eventId,
            scenarioId = scenarioId,
            groupId = groupId,
            conditions = listOf(eventCondition),
            actions = listOf(testsData.newPauseAction(eventId)),
        )

        val testScenario = testsData.newTestScenario(
            scenarioId = scenarioId,
            imageEvents = listOf(event),
        )

        mockBitmapSupplier.apply {
            mockBitmapProviding(gateCondition)
            mockBitmapProviding(eventCondition)
        }
        mockScalingManager.apply {
            mockScaling(gateCondition)
            mockScaling(eventCondition)
        }
        mockImageDetector.apply {
            mockDetectionResult(gateCondition, false)
            mockDetectionResult(eventCondition, true)
        }

        ScenarioProcessor(
            processingTag = "tests",
            scenarioName = testScenario.scenario.name,
            scalingManager = mockScalingManager,
            randomize = false,
            imageEvents = testScenario.imageEvents,
            triggerEvents = emptyList(),
            imageGroups = listOf(group),
            triggerGroups = emptyList(),
            imageDetector = mockImageDetector,
            androidExecutor = mockAndroidExecutor,
            bitmapSupplier = mockBitmapSupplier::getBitmap,
            onStopRequested = {},
            progressListener = mockProcessingListener,
        ).process(testsData.newMockedScreenBitmap())

        mockImageDetector.verifyConditionNeverProcessed(eventCondition)
    }

    @Test
    fun `Nested group events skipped when parent gate is inactive`() = runTest {
        val scenarioId = testsData.newScenarioId()
        val parentGroupId = testsData.newEventGroupId()
        val childGroupId = testsData.newEventGroupId()
        val eventId = testsData.newEventId()
        val parentGateCondition = testsData.newTestImageCondition(parentGroupId, shouldBeDetected = true)
        val childGateCondition = testsData.newTestImageCondition(childGroupId, shouldBeDetected = true)
        val eventCondition = testsData.newTestImageCondition(eventId, shouldBeDetected = true)

        val parentGroup = EventGroup(
            id = parentGroupId,
            scenarioId = scenarioId,
            name = "Parent",
            eventType = GroupEventType.IMAGE,
            conditionOperator = AND,
            priority = 0,
            conditions = listOf(parentGateCondition.imageCondition),
            parentGroupId = null,
        )
        val childGroup = EventGroup(
            id = childGroupId,
            scenarioId = scenarioId,
            name = "Child",
            eventType = GroupEventType.IMAGE,
            conditionOperator = AND,
            priority = 0,
            conditions = listOf(childGateCondition.imageCondition),
            parentGroupId = parentGroupId,
        )
        val event = testsData.newTestImageEvent(
            eventId = eventId,
            scenarioId = scenarioId,
            groupId = childGroupId,
            conditions = listOf(eventCondition),
            actions = listOf(testsData.newPauseAction(eventId)),
        )

        val testScenario = testsData.newTestScenario(
            scenarioId = scenarioId,
            imageEvents = listOf(event),
        )

        mockBitmapSupplier.apply {
            mockBitmapProviding(parentGateCondition)
            mockBitmapProviding(childGateCondition)
            mockBitmapProviding(eventCondition)
        }
        mockScalingManager.apply {
            mockScaling(parentGateCondition)
            mockScaling(childGateCondition)
            mockScaling(eventCondition)
        }
        mockImageDetector.apply {
            mockDetectionResult(parentGateCondition, false)
            mockDetectionResult(childGateCondition, true)
            mockDetectionResult(eventCondition, true)
        }

        ScenarioProcessor(
            processingTag = "tests",
            scenarioName = testScenario.scenario.name,
            scalingManager = mockScalingManager,
            randomize = false,
            imageEvents = testScenario.imageEvents,
            triggerEvents = emptyList(),
            imageGroups = listOf(parentGroup, childGroup),
            triggerGroups = emptyList(),
            imageDetector = mockImageDetector,
            androidExecutor = mockAndroidExecutor,
            bitmapSupplier = mockBitmapSupplier::getBitmap,
            onStopRequested = {},
            progressListener = mockProcessingListener,
        ).process(testsData.newMockedScreenBitmap())

        mockImageDetector.verifyConditionNeverProcessed(eventCondition)
    }

    @Test
    fun `Nested group events skipped when child gate is inactive`() = runTest {
        val scenarioId = testsData.newScenarioId()
        val parentGroupId = testsData.newEventGroupId()
        val childGroupId = testsData.newEventGroupId()
        val eventId = testsData.newEventId()
        val parentGateCondition = testsData.newTestImageCondition(parentGroupId, shouldBeDetected = true)
        val childGateCondition = testsData.newTestImageCondition(childGroupId, shouldBeDetected = true)
        val eventCondition = testsData.newTestImageCondition(eventId, shouldBeDetected = true)

        val parentGroup = EventGroup(
            id = parentGroupId,
            scenarioId = scenarioId,
            name = "Parent",
            eventType = GroupEventType.IMAGE,
            conditionOperator = AND,
            priority = 0,
            conditions = listOf(parentGateCondition.imageCondition),
            parentGroupId = null,
        )
        val childGroup = EventGroup(
            id = childGroupId,
            scenarioId = scenarioId,
            name = "Child",
            eventType = GroupEventType.IMAGE,
            conditionOperator = AND,
            priority = 0,
            conditions = listOf(childGateCondition.imageCondition),
            parentGroupId = parentGroupId,
        )
        val event = testsData.newTestImageEvent(
            eventId = eventId,
            scenarioId = scenarioId,
            groupId = childGroupId,
            conditions = listOf(eventCondition),
            actions = listOf(testsData.newPauseAction(eventId)),
        )

        val testScenario = testsData.newTestScenario(
            scenarioId = scenarioId,
            imageEvents = listOf(event),
        )

        mockBitmapSupplier.apply {
            mockBitmapProviding(parentGateCondition)
            mockBitmapProviding(childGateCondition)
            mockBitmapProviding(eventCondition)
        }
        mockScalingManager.apply {
            mockScaling(parentGateCondition)
            mockScaling(childGateCondition)
            mockScaling(eventCondition)
        }
        mockImageDetector.apply {
            mockDetectionResult(parentGateCondition, true)
            mockDetectionResult(childGateCondition, false)
            mockDetectionResult(eventCondition, true)
        }

        ScenarioProcessor(
            processingTag = "tests",
            scenarioName = testScenario.scenario.name,
            scalingManager = mockScalingManager,
            randomize = false,
            imageEvents = testScenario.imageEvents,
            triggerEvents = emptyList(),
            imageGroups = listOf(parentGroup, childGroup),
            triggerGroups = emptyList(),
            imageDetector = mockImageDetector,
            androidExecutor = mockAndroidExecutor,
            bitmapSupplier = mockBitmapSupplier::getBitmap,
            onStopRequested = {},
            progressListener = mockProcessingListener,
        ).process(testsData.newMockedScreenBitmap())

        mockImageDetector.verifyConditionNeverProcessed(eventCondition)
    }
}
