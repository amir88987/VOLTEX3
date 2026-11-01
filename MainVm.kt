package app.videogir

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface Ui {
    data object Idle : Ui
    data object Loading : Ui
    data class Error(val message: String) : Ui
    data class Ready(val data: Loaded) : Ui
}

class MainVm : ViewModel() {
    var input by mutableStateOf("")
    var ui by mutableStateOf<Ui>(Ui.Idle)
        private set

    private var job: Job? = null

    fun load() {
        if (input.isBlank()) return
        job?.cancel()
        ui = Ui.Loading
        job = viewModelScope.launch {
            ui = try {
                Ui.Ready(withContext(Dispatchers.IO) { Extractor.load(input) })
            } catch (e: UserError) {
                Ui.Error(e.message ?: "خطا")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Ui.Error("خطای ناشناخته: ${e.message}")
            }
        }
    }
}
