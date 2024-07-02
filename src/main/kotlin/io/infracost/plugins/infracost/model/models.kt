package io.infracost.plugins.infracost.model

import com.fasterxml.jackson.annotation.JsonProperty
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.SystemIndependent
import java.nio.file.Path
import java.nio.file.Paths
import javax.swing.event.TreeModelListener
import javax.swing.tree.TreeModel
import javax.swing.tree.TreePath
import kotlin.io.path.relativeTo

private val treeModelListeners = ArrayList<TreeModelListener>()

class Breakdown {
    @get:JsonProperty("resources")
    var resources: ArrayList<Resource>? = null

    @get:JsonProperty("totalMonthlyCost")
    var totalMonthlyCost: String? = null
}

class Metadata {
    @get:JsonProperty("path")
    var path: String? = null

    @get:JsonProperty("filename")
    var filename: String? = null

    @get:JsonProperty("startLine")
    var startLine: Int = 0
}

class Project {
    @get:JsonProperty("name")
    var name: String? = null

    @get:JsonProperty("displayName")
    var displayName: String? = null

    @get:JsonProperty("metadata")
    var metadata: Metadata? = null

    @get:JsonProperty("breakdown")
    var breakdown: Breakdown? = null
}

class Resource {
    @get:JsonProperty("name")
    var name: String? = null

    @get:JsonProperty("resourceType")
    var resourceType: String? = null

    @get:JsonProperty("metadata")
    var metadata: Metadata? = null

    @get:JsonProperty("hourlyCost")
    var hourlyCost: String? = null

    @get:JsonProperty("monthlyCost")
    var monthlyCost: String? = null

    @get:JsonProperty("monthlyUsageCost")
    var monthlyUsageCost: String? = null

    override fun toString(): String {
        return String.format("%s - $%.2f", name, monthlyCost?.toFloat())
    }
}

class Results {
    @get:JsonProperty("version")
    var version: String? = null

    @get:JsonProperty("metadata")
    var metadata: Metadata? = null

    @get:JsonProperty("currency")
    var currency: String? = null

    @get:JsonProperty("projects")
    var projects: ArrayList<Project>? = null

    @get:JsonProperty("totalHourlyCost")
    var totalHourlyCost: String? = null

    @get:JsonProperty("totalMonthlyCost")
    var totalMonthlyCost: String? = null

    @get:JsonProperty("totalMonthlyUsageCost")
    var totalMonthlyUsageCost: String? = null

    @get:JsonProperty("pastTotalHourlyCost")
    var pastTotalHourlyCost: String? = null

    @get:JsonProperty("pastTotalMonthlyCost")
    var pastTotalMonthlyCost: String? = null

    @get:JsonProperty("pastTotalMonthlyUsageCost")
    var pastTotalMonthlyUsageCost: String? = null

    @get:JsonProperty("diffTotalHourlyCost")
    var diffTotalHourlyCost: String? = null

    @get:JsonProperty("diffTotalMonthlyCost")
    var diffTotalMonthlyCost: String? = null

    @get:JsonProperty("diffTotalMonthlyUsageCost")
    var diffTotalMonthlyUsageCost: String? = null
}

class File(
    val filename: String,
    projectPath: String,
    private val monthlyCost: Float,
    val resources: ArrayList<Resource>
) : TreePath(filename) {
    private val prettyFilename: String =
        Path.of(filename).toRealPath().relativeTo(Path.of(projectPath)).toString()

    override fun toString(): String {
        return String.format("%s - $%.2f", prettyFilename, monthlyCost)
    }

    override fun getPath(): Array<Any> {
        return arrayOf(this.prettyFilename)
    }
}

class InfracostProject(project: Project, basePath: @SystemIndependent @NonNls String) :
    TreePath(project.displayName!!) {
    val name: String = project.displayName!!
    private val monthlyCost: Float = project.breakdown?.totalMonthlyCost?.toFloat() ?: 0.0f
    val files = ArrayList<File>()

    override fun toString(): String {
        return String.format("%s - $%.2f", name, monthlyCost)
    }

    init {
        val fileResources = hashMapOf<String, ArrayList<Resource>>()
        for (resource in project.breakdown?.resources!!) {
            val filename = resource.metadata?.filename ?: "Unknown"
            fileResources.putIfAbsent(filename, ArrayList())
            fileResources[filename]?.add(resource)
        }
        fileResources.forEach { (filename, resources) ->
            var monthlyFileCost = 0.0f
            resources.forEach { resource -> monthlyFileCost += resource.monthlyCost?.toFloat() ?: 0.0f }
            val resourceFilePath = Paths.get(basePath, filename).toString()
            if (Path.of(resourceFilePath).toFile().exists()) {
                files.add(File(resourceFilePath, basePath, monthlyFileCost, resources))
            }
        }
    }
}

class InfracostModel(results: Results?, basePath: @SystemIndependent @NonNls String) : TreeModel,
    TreePath("Infracost Model") {
    val projects = ArrayList<InfracostProject>()

    init {
        if (results != null) {
            results.projects?.forEach { project -> projects.add(InfracostProject(project, basePath)) }
        }
    }

    override fun toString(): String {
        return "Infracost Model"
    }

    override fun getRoot(): Any {
        return this
    }

    override fun getChild(parent: Any?, index: Int): Any {
        return when (parent) {
            is InfracostModel -> {
                projects[index]
            }

            is InfracostProject -> {
                parent.files[index]
            }

            is File -> {
                parent.resources[index]
            }

            else -> {
                throw IllegalArgumentException("Unknown parent type")
            }
        }
    }

    override fun getChildCount(parent: Any?): Int {
        return when (parent) {
            is InfracostModel -> {
                projects.size
            }

            is InfracostProject -> {
                parent.files.size
            }

            is File -> {
                parent.resources.size
            }

            else -> {
                throw IllegalArgumentException("Unknown parent type")
            }
        }
    }

    override fun isLeaf(node: Any?): Boolean {
        return when (node) {
            is InfracostModel -> {
                false
            }

            is InfracostProject -> {
                false
            }

            is File -> {
                false
            }

            is Resource -> {
                true
            }

            else -> {
                throw IllegalArgumentException("Unknown node type")
            }
        }
    }

    override fun valueForPathChanged(path: TreePath?, newValue: Any?) {
        TODO("Not yet implemented")
    }

    override fun getIndexOfChild(parent: Any?, child: Any?): Int {
        return when (parent) {
            is InfracostModel -> {
                projects.indexOf(child)
            }

            is InfracostProject -> {
                parent.files.indexOf(child)
            }

            is File -> {
                parent.resources.indexOf(child)
            }

            else -> {
                throw IllegalArgumentException("Unknown parent type")
            }
        }
    }

    override fun addTreeModelListener(l: TreeModelListener?) {
        treeModelListeners.add(l!!)
    }

    override fun removeTreeModelListener(l: TreeModelListener?) {
        treeModelListeners.remove(l!!)
    }
}
