package com.unshoo.pixelmusic.presentation.screens.youtube

import android.webkit.CookieManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.unshoo.pixelmusic.data.remote.youtube.Constants
import com.unshoo.pixelmusic.data.remote.youtube.DatastoreRepository
import com.unshoo.pixelmusic.data.remote.youtube.UmihiHelper.printd
import com.unshoo.pixelmusic.data.model.youtube.Cookies
import com.unshoo.pixelmusic.data.worker.SyncManager
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
    private val syncManager: SyncManager
) : ViewModel() {
    private val _uiState = MutableStateFlow(SettingsState())
    //  val uiState = _uiState.asStateFlow()

    private val _eventsChannel = MutableSharedFlow<ScreenEvent.Out>()
    val eventFlow = _eventsChannel.asSharedFlow()

    fun onPageFinished(url: String?) {
        viewModelScope.launch {
            if (url?.contains(Constants.Auth.END_URL) == true && !_uiState.value.isLoggedIn) {
                val cookies = CookieManager.getInstance().getCookie(url).orEmpty()
                saveCookies(Cookies(cookies))
                _uiState.update { it.copy(isLoggedIn = true) }
                _eventsChannel.emit(ScreenEvent.Out.LoginCompleted)
                // Trigger an immediate background synchronization of user playlists and library
                syncManager.fullSync()
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
        printd("Got cookies: $cookies")
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
