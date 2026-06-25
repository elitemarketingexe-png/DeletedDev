package com.unshoo.pixelmusic.presentation.screens.youtube

import android.webkit.CookieManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.unshoo.pixelmusic.data.remote.youtube.DatastoreRepository
import com.unshoo.pixelmusic.data.model.youtube.Cookies
import com.unshoo.pixelmusic.data.worker.SyncManager
import com.unshoo.pixelmusic.data.worker.YouTubeLibrarySyncManager
import unshoo.ianshulyadav.pixelmusic.innertube.YouTube
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val datastoreRepository: DatastoreRepository,
    private val syncManager: SyncManager,
    private val youTubeLibrarySyncManager: YouTubeLibrarySyncManager
) : ViewModel() {
    private val _uiState = MutableStateFlow(SettingsState())

    private val _eventsChannel = MutableSharedFlow<ScreenEvent.Out>()
    val eventFlow = _eventsChannel.asSharedFlow()

    fun onPageFinished(url: String?) {
        viewModelScope.launch {
            val cookieManager = CookieManager.getInstance()
            val cookies = cookieManager.getCookie("https://music.youtube.com").orEmpty()
            if ((cookies.contains("SAPISID") || cookies.contains("__Secure-3PAPISID")) && !_uiState.value.isLoggedIn) {
                saveCookies(Cookies(cookies))
                _uiState.update { it.copy(isLoggedIn = true) }
                _eventsChannel.emit(ScreenEvent.Out.LoginCompleted)
                launch(kotlinx.coroutines.Dispatchers.IO) { youTubeLibrarySyncManager.syncNow(force = true) }
                syncManager.incrementalSync()
            }
        }
    }

    fun onDataSyncIdFound(dataSyncId: String) {
        viewModelScope.launch {
            datastoreRepository.saveDataSyncId(dataSyncId)
            YouTube.dataSyncId = dataSyncId
        }
    }

    private fun saveCookies(cookies: Cookies) {
        viewModelScope.launch {
            datastoreRepository.saveCookies(cookies)
            YouTube.cookie = cookies.toRawCookie()
        }
    }

    sealed interface ScreenEvent {
        sealed class Out {
            object LoginCompleted : Out()
        }
    }
}
