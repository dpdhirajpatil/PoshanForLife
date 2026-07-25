package com.poshanforlife.android.feature.patient.track

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.poshanforlife.android.core.datastore.Goals
import com.poshanforlife.android.core.datastore.GoalsDataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class GoalsViewModel @Inject constructor(
    private val goalsDataStore: GoalsDataStore,
) : ViewModel() {

    val goals: StateFlow<Goals> = goalsDataStore.goals
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Goals())

    fun save(goals: Goals) {
        viewModelScope.launch { goalsDataStore.save(goals) }
    }
}
