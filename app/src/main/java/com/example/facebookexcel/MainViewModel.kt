package com.example.facebookexcel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.facebookexcel.data.AppDatabase
import com.example.facebookexcel.data.FacebookItem
import com.example.facebookexcel.facebook.FacebookApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = AppDatabase.get(app).facebookItemDao()
    private val api = FacebookApiClient()

    val items: StateFlow<List<FacebookItem>> =
        dao.observeAll().stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            emptyList()
        )

    private val _loadingIds = MutableStateFlow<Set<Long>>(emptySet())
    val loadingIds: StateFlow<Set<Long>> = _loadingIds

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    fun clearMessage() {
        _message.value = null
    }

    fun update(item: FacebookItem, link: String = item.link, title: String = item.title,
               time: String = item.time, image: String = item.image) {
        viewModelScope.launch {
            dao.update(item.copy(link = link, title = title, time = time, image = image))
        }
    }

    fun addEmptyRow() {
        viewModelScope.launch {
            dao.insert(FacebookItem())
        }
    }

    fun addLinkRow(link: String) {
        val clean = link.trim()
        if (clean.isBlank()) return
        viewModelScope.launch {
            dao.insert(FacebookItem(link = clean))
        }
    }

    fun delete(item: FacebookItem) {
        viewModelScope.launch { dao.delete(item) }
    }

    fun loadFacebook(item: FacebookItem, token: String) {
        if (item.link.isBlank()) {
            _message.value = "Hãy nhập link Facebook trước."
            return
        }

        viewModelScope.launch {
            _loadingIds.update { it + item.id }

            val result = withContext(Dispatchers.IO) {
                api.load(item.link.trim(), token.trim())
            }

            result.onSuccess { data ->
                dao.update(
                    item.copy(
                        title = data.title.ifBlank { item.title },
                        time = data.time.ifBlank { item.time },
                        image = data.image.ifBlank { item.image }
                    )
                )
                _message.value = "Đã LOAD: ${item.link}"
            }.onFailure {
                _message.value = it.message ?: "Không thể LOAD dữ liệu Facebook."
            }

            _loadingIds.update { it - item.id }
        }
    }

    fun saveAll() {
        viewModelScope.launch {
            // Room updates happen as the user edits each cell.
            // This button forces a complete write of the current table.
            dao.insertAll(items.value)
            _message.value = "Đã SAVE ${items.value.size} dòng vào SQLite."
        }
    }
}
