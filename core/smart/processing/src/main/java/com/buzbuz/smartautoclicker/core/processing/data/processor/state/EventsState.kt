/*
 * Copyright (C) 2024 Kevin Buzeau
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.buzbuz.smartautoclicker.core.processing.data.processor.state

import com.buzbuz.smartautoclicker.core.domain.model.event.Event
import com.buzbuz.smartautoclicker.core.domain.model.event.EventGroup
import com.buzbuz.smartautoclicker.core.domain.model.event.ImageEvent
import com.buzbuz.smartautoclicker.core.domain.model.event.TriggerEvent
import com.buzbuz.smartautoclicker.core.domain.model.event.sortedForGroupListProcessing

interface IEventsState {

    fun isEventEnabled(eventId: Long): Boolean
    fun isEventOnCooldown(eventId: Long): Boolean
    fun startEventCooldown(event: Event)
    fun areAllEventsDisabled(): Boolean
    fun areAllImageEventsDisabled(): Boolean
    fun areAllTriggerEventsDisabled(): Boolean

    fun getEnabledImageEvents(): Collection<ImageEvent>
    fun getEnabledTriggerEvents(): Collection<TriggerEvent>

    fun enableAll()
    fun enableEvent(eventId: Long)
    fun enableEventsWithNamePrefix(prefix: String)
    fun disableAll()
    fun disableEvent(eventId: Long)
    fun disableEventsWithNamePrefix(prefix: String)
    fun toggleAll()
    fun toggleEvent(eventId: Long)
    fun toggleEventsWithNamePrefix(prefix: String)
    fun setEventStateListener(listener: EventStateListener)
}

interface EventStateListener {
    fun onEventEnabled(event: Event)
    fun onEventDisabled(event: Event)
}

/**
 * Handle the state of the scenario events.
 *
 * Maintains two maps:
 *  - the enabled events map: those are the events that will be processed.
 *  - the disabled events map: those are the events that will be skipped.
 * Handles the ToggleEvent actions and move the events between those maps accordingly.
 */
internal class EventsState(
    imageEvents: List<ImageEvent>,
    triggerEvents: List<TriggerEvent>,
    imageGroups: List<EventGroup> = emptyList(),
    triggerGroups: List<EventGroup> = emptyList(),
) : IEventsState {

    private val runnableImageEvents = imageEvents.filterNot { it.ignored }
    private val runnableTriggerEvents = triggerEvents.filterNot { it.ignored }

    private val imageEventsProcessingOrder: List<ImageEvent> =
        runnableImageEvents.sortedForGroupListProcessing(imageGroups)
    private val triggerEventsProcessingOrder: List<TriggerEvent> =
        runnableTriggerEvents.sortedForGroupListProcessing(triggerGroups)

    /** Monitor the state of all image events. */
    private val imageEventList: EventList<ImageEvent> = EventList(runnableImageEvents)
    /** Monitor the state of all trigger events. */
    private val triggerEventList: EventList<TriggerEvent> = EventList(runnableTriggerEvents)

    override fun setEventStateListener(listener: EventStateListener) {
        triggerEventList.eventEnabledListener = listener
        imageEventList.eventEnabledListener = listener
    }

    override fun isEventEnabled(eventId: Long): Boolean =
        triggerEventList.isEventEnabled(eventId) || imageEventList.isEventEnabled(eventId)

    override fun isEventOnCooldown(eventId: Long): Boolean =
        triggerEventList.isEventOnCooldown(eventId) || imageEventList.isEventOnCooldown(eventId)

    override fun startEventCooldown(event: Event) {
        imageEventList.startEventCooldown(event)
        triggerEventList.startEventCooldown(event)
    }

    override fun areAllEventsDisabled(): Boolean =
        imageEventList.areAllEventsDisabled() && triggerEventList.areAllEventsDisabled()

    override fun areAllImageEventsDisabled(): Boolean =
        imageEventList.areAllEventsDisabled()

    override fun getEnabledImageEvents(): Collection<ImageEvent> {
        val enabled = imageEventList.getEnabledEvents().associateBy { it.getValidId() }
        return imageEventsProcessingOrder.mapNotNull { enabled[it.getValidId()] }
    }

    override fun areAllTriggerEventsDisabled(): Boolean =
        triggerEventList.areAllEventsDisabled()

    override fun getEnabledTriggerEvents(): Collection<TriggerEvent> {
        val enabled = triggerEventList.getEnabledEvents().associateBy { it.getValidId() }
        return triggerEventsProcessingOrder.mapNotNull { enabled[it.getValidId()] }
    }

    override fun enableEvent(eventId: Long) {
        imageEventList.enableEvent(eventId)
        triggerEventList.enableEvent(eventId)
    }

    override fun enableEventsWithNamePrefix(prefix: String) {
        imageEventList.enableEventsWithNamePrefix(prefix)
        triggerEventList.enableEventsWithNamePrefix(prefix)
    }

    override fun disableEvent(eventId: Long) {
        imageEventList.disableEvent(eventId)
        triggerEventList.disableEvent(eventId)
    }

    override fun disableEventsWithNamePrefix(prefix: String) {
        imageEventList.disableEventsWithNamePrefix(prefix)
        triggerEventList.disableEventsWithNamePrefix(prefix)
    }

    override fun toggleEvent(eventId: Long) {
        imageEventList.toggleEvent(eventId)
        triggerEventList.toggleEvent(eventId)
    }

    override fun toggleEventsWithNamePrefix(prefix: String) {
        imageEventList.toggleEventsWithNamePrefix(prefix)
        triggerEventList.toggleEventsWithNamePrefix(prefix)
    }

    override fun enableAll() {
        imageEventList.enableAll()
        triggerEventList.enableAll()
    }

    override fun disableAll() {
        imageEventList.disableAll()
        triggerEventList.disableAll()
    }

    override fun toggleAll() {
        imageEventList.toggleAll()
        triggerEventList.toggleAll()
    }
}

private class EventList<T : Event>(events: List<T>) {

    /** Set of enabled events ids. */
    private val enabledEventsMap: MutableMap<Long, T> = mutableMapOf()
    /** Cooldown end timestamps by event id, in elapsed realtime milliseconds. */
    private val cooldownEndTimestamps: MutableMap<Long, Long> = mutableMapOf()
    /** Map of the all events. */
    private val eventsMap: Map<Long, T> = buildMap {
        events.forEach { event ->
            if (event.enabledOnStart) enabledEventsMap[event.getValidId()] = event
            put(event.getValidId(), event)
        }
    }

    var eventEnabledListener: EventStateListener? = null

    fun isEventEnabled(eventDbId: Long): Boolean =
        enabledEventsMap.containsKey(eventDbId)

    fun isEventOnCooldown(eventDbId: Long): Boolean {
        val cooldownEndTimestamp = cooldownEndTimestamps[eventDbId] ?: return false
        if (android.os.SystemClock.elapsedRealtime() < cooldownEndTimestamp) return true

        cooldownEndTimestamps.remove(eventDbId)
        return false
    }

    fun startEventCooldown(event: Event) {
        val eventId = event.getValidId()
        val cooldownMs = event.cooldownMs
        if (!eventsMap.containsKey(eventId) || cooldownMs <= 0) return

        cooldownEndTimestamps[eventId] = android.os.SystemClock.elapsedRealtime() + cooldownMs
    }

    fun areAllEventsDisabled(): Boolean =
        enabledEventsMap.isEmpty()

    fun getEnabledEvents(): Collection<T> =
        enabledEventsMap.values

    fun enableEvent(eventId: Long) {
        if (enabledEventsMap.containsKey(eventId)) return
        val event = eventsMap[eventId] ?: return

        enabledEventsMap[eventId] = event
        eventEnabledListener?.onEventEnabled(event)
    }

    fun disableEvent(eventId: Long) {
        if (!enabledEventsMap.containsKey(eventId)) return
        val event = eventsMap[eventId] ?: return

        enabledEventsMap.remove(eventId)
        eventEnabledListener?.onEventDisabled(event)
    }

    fun toggleEvent(eventId: Long) {
        if (enabledEventsMap.containsKey(eventId)) disableEvent(eventId)
        else enableEvent(eventId)
    }

    fun enableAll() {
        eventsMap.keys.forEach(::enableEvent)
    }

    fun disableAll() {
        eventsMap.keys.forEach(::disableEvent)
    }

    fun toggleAll() {
        eventsMap.keys.forEach(::toggleEvent)
    }

    fun enableEventsWithNamePrefix(prefix: String) {
        eventsMap.values
            .filter { it.name.startsWith(prefix) }
            .forEach { enableEvent(it.getValidId()) }
    }

    fun disableEventsWithNamePrefix(prefix: String) {
        eventsMap.values
            .filter { it.name.startsWith(prefix) }
            .forEach { disableEvent(it.getValidId()) }
    }

    fun toggleEventsWithNamePrefix(prefix: String) {
        eventsMap.values
            .filter { it.name.startsWith(prefix) }
            .forEach { toggleEvent(it.getValidId()) }
    }
}
