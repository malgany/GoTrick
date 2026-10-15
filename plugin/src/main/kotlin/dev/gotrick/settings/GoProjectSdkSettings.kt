package dev.gotrick.settings

import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project

@Service(Service.Level.PROJECT)
@State(name = "GoTrickProjectSdk", storages = [Storage("gotrick-sdk.xml")])
class GoProjectSdkSettings : PersistentStateComponent<GoProjectSdkSettings.Data> {
    data class Data(var home: String = "")
    private var data = Data()
    override fun getState() = data
    override fun loadState(state: Data) { data = state }
    var home: String
        get() = data.home
        set(value) { data.home = value }
    companion object {
        fun getInstance(project: Project): GoProjectSdkSettings = project.getService(GoProjectSdkSettings::class.java)
    }
}
