package com.infracost.intellij

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory

class InfracostStatusBarWidget(private val project: Project) : StatusBarWidget {

    private var statusBar: StatusBar? = null
    private var text: String? = null

    override fun ID(): String = ID

    override fun install(statusBar: StatusBar) {
        this.statusBar = statusBar
    }

    override fun dispose() {
        statusBar = null
        text = null
    }

    override fun getPresentation(): StatusBarWidget.WidgetPresentation {
        return object : StatusBarWidget.TextPresentation {
            override fun getText(): String = text ?: ""
            override fun getTooltipText(): String = text ?: ""
            @Suppress("DEPRECATION")
            override fun getAlignment(): Float = 0f
        }
    }

    fun show(message: String) {
        text = message
        statusBar?.updateWidget(ID)
    }

    fun clear() {
        text = null
        statusBar?.updateWidget(ID)
    }

    companion object {
        const val ID = "InfracostStatusBar"

        fun getInstance(project: Project): InfracostStatusBarWidget? {
            val statusBar = com.intellij.openapi.wm.WindowManager.getInstance().getStatusBar(project) ?: return null
            return statusBar.getWidget(ID) as? InfracostStatusBarWidget
        }
    }
}

class InfracostStatusBarWidgetFactory : StatusBarWidgetFactory {
    override fun getId(): String = InfracostStatusBarWidget.ID
    override fun getDisplayName(): String = "Infracost"
    override fun createWidget(project: Project): StatusBarWidget = InfracostStatusBarWidget(project)
}
