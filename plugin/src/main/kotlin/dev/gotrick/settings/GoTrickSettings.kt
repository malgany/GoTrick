package dev.gotrick.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

/** User choices are persisted; an empty path deliberately means automatic discovery. */
@Service(Service.Level.APP)
@State(name = "GoTrickSettings", storages = [Storage("gotrick.xml")])
class GoTrickSettings : PersistentStateComponent<GoTrickSettings.Data> {
    data class Data(
        var goExecutable: String = "",
        var goplsExecutable: String = "",
        var delveExecutable: String = "",
        var formatOnSave: Boolean = true,
    )

    private var data = Data()
    override fun getState(): Data = data
    override fun loadState(state: Data) { data = state }

    var goExecutable: String
        get() = data.goExecutable
        set(value) { data.goExecutable = value }
    var goplsExecutable: String
        get() = data.goplsExecutable
        set(value) { data.goplsExecutable = value }
    var delveExecutable: String
        get() = data.delveExecutable
        set(value) { data.delveExecutable = value }
    var formatOnSave: Boolean
        get() = data.formatOnSave
        set(value) { data.formatOnSave = value }

    companion object {
        @JvmStatic fun getInstance(): GoTrickSettings =
            ApplicationManager.getApplication().getService(GoTrickSettings::class.java)
    }
}
