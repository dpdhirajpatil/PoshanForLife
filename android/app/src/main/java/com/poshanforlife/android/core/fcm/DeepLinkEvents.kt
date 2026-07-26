package com.poshanforlife.android.core.fcm

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/** relatedEntityType/relatedEntityId, same shape the push payload and the in-app notification list both carry. */
data class DeepLinkTarget(val relatedEntityType: String?, val relatedEntityId: String?)

/**
 * Fired by MainActivity (from a tapped push notification's Intent extras) and
 * by the bell-icon dropdown (a direct call, no Intent involved); observed by
 * AppNavGraph to resolve a route within whichever role graph is active. A
 * SharedFlow (not a StateFlow) so a second identical tap still re-navigates
 * rather than being deduped by equal-value skipping.
 */
@Singleton
class DeepLinkEvents @Inject constructor() {
    private val _events = MutableSharedFlow<DeepLinkTarget>(extraBufferCapacity = 1)
    val events: SharedFlow<DeepLinkTarget> = _events.asSharedFlow()

    fun emit(relatedEntityType: String?, relatedEntityId: String?) {
        _events.tryEmit(DeepLinkTarget(relatedEntityType, relatedEntityId))
    }
}
