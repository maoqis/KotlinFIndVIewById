package actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.fileTypes.FileTypes
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.psi.PsiFile
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiUtilBase
import com.intellij.psi.xml.XmlFile
import com.intellij.psi.xml.XmlTag
import com.intellij.ui.EditorTextField
import constant.Constant
import entitys.Element
import fileAccept
import getLayoutName
import org.apache.commons.lang.StringUtils
import showPopupBalloon
import java.awt.Color
import java.util.*

/**
 * @author Jowan
 */
class DelegateViewByIdAction : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        //当前文件/当前光标位置




        val project = e.getData(PlatformDataKeys.PROJECT)
        val mEditor = e.getData(PlatformDataKeys.EDITOR)

        val file = PsiUtilBase.getPsiFileInEditor(mEditor!!, project!!)




        // 获取布局文件，通过FilenameIndex.getFilesByName获取GlobalSearchScope.allScope(project)搜索整个项目
        val popupTime = 5
        val mSelectedText: String? = mEditor.selectionModel.selectedText ?: let {
            Messages.showInputDialog(project,
                    Constant.Action.SELECTED_MESSAGE,
                    Constant.Action.SELECTED_TITLE,
                    Messages.getInformationIcon()) ?: let {
                mEditor.showPopupBalloon(Constant.Action.SELECTED_ERROR_NO_NAME, popupTime)
                return
            }
        }

        val psiFiles = FilenameIndex.getFilesByName(project,
                mSelectedText + Constant.SELECTED_TEXT_SUFFIX,
                GlobalSearchScope.allScope(project))
        if (psiFiles.isEmpty()) {
            mEditor.showPopupBalloon(Constant.Action.SELECTED_ERROR_NO_SELECTED, popupTime)
            return
        }

        val xmlFile = if (psiFiles.size > 1) {
            val psiFilePath = file!!.parent?.toString()!!
            val psiFiles1 = psiFiles.filter {
                val modulePath = it.parent?.toString()!!
                modulePath.contains("\\src\\main\\res\\layout") && psiFilePath.substring(0, psiFilePath.indexOf("\\main\\")) == modulePath.substring(0, modulePath.indexOf("\\main\\"))
            }
            if (psiFiles1.isEmpty()) {
                mEditor.showPopupBalloon(Constant.Action.SELECTED_ERROR_NO_SELECTED, popupTime)
                return
            } else psiFiles1[0] as XmlFile
        } else {
            psiFiles[0] as XmlFile
        }



        try {

            println(file!!.text)

            var elements = ArrayList<Element>()
            getIDsFromLayoutToList(xmlFile, elements)


            //获取TextView的class
            val block = StringBuilder()
            var findPre: String? = "itemView"

            for (element in elements) {
                if (element.isEnable) {
                    // 判断是否已存在findViewById
                    var isFdExist = false
                    var pre = findPre?.let { it + "." } ?: ""
                    var inflater = ""

                    val casts = if(true) ": ${element.name} " else ""

                    val findViewById = "val ${element.fieldName}$inflater$casts= ${pre}findViewById(${element.fullID})"

                    block.append("$findViewById\n")


                }
            }

            //显示文字(弹出框形式)
            showEditText(block.toString(), mEditor)
        } catch (e1: Exception) {
            e1.printStackTrace()
            showDialog(mEditor, "无法找到该布局文件,或布局文件不唯一 " + e1.message, 2)
        }

    }



    /**
     * 显示提示框
     */
    private fun showDialog(editor: Editor, result: String, time: Int) {
        ApplicationManager.getApplication().invokeLater {
            val factory = JBPopupFactory.getInstance()
            factory.createHtmlTextBalloonBuilder(result, null, Color.gray, null)
                    .setFadeoutTime((time * 1000).toLong())
                    .createBalloon()
                    .show(factory.guessBestPopupLocation(editor), Balloon.Position.below)
        }
    }


    /**
     * 显示文本框
     */
    private fun showEditText(result: String, editor: Editor) {
        val instance = JBPopupFactory.getInstance()
        instance.createDialogBalloonBuilder(EditorTextField(EditorFactory.getInstance().createDocument(result), null, FileTypes.PLAIN_TEXT, false, false), "KViewBind-Generate")
                .setHideOnKeyOutside(true)
                .setHideOnClickOutside(true)
                .createBalloon()
                .show(instance.guessBestPopupLocation(editor), Balloon.Position.below)
    }

    /**
     * 获取所有id
     * @param file     file
     *
     * @param elements elements
     */
    fun getIDsFromLayoutToList(psiFile: PsiFile, elements: ArrayList<Element>) {
        psiFile.fileAccept { element ->
            // 解析Xml标签
            if (element is XmlTag) {
                with(element) {
                    // 获取Tag的名字（TextView）或者自定义
                    val name: String = name
                    // 如果有include
                    if (name.equals("include", ignoreCase = true)) {
                        // 获取布局
                        val layout = getAttribute("layout", null) ?: return@fileAccept
                        val layoutName = layout.value.getLayoutName() ?: return@fileAccept
                        // 获取project
                        val project = this.project
                        // 布局文件
                        var include: XmlFile? = null
                        val psiFiles = FilenameIndex.getFilesByName(project, layoutName + Constant.SELECTED_TEXT_SUFFIX, GlobalSearchScope.allScope(project))
                        if (psiFiles.isNotEmpty()) {
                            include = if (psiFiles.size > 1) {
                                val psiFilePath = psiFile.parent?.toString()!!
                                val psiFiles1 = psiFiles.filter {
                                    val modulePath = it.parent?.toString()!!
                                    modulePath.contains("\\src\\main\\res\\layout") && psiFilePath.substring(0, psiFilePath.indexOf("\\main\\")) == modulePath.substring(0, modulePath.indexOf("\\main\\"))
                                }
                                if (psiFiles1.isEmpty()) return@fileAccept else psiFiles1[0] as XmlFile
                            } else {
                                psiFiles[0] as XmlFile
                            }
                        }
                        if (include != null) {
                            // 递归
                            getIDsFromLayoutToList(include, elements)
                            return@fileAccept
                        }
                    }
                    // 获取id字段属性
                    val id = getAttribute("android:id", null) ?: return@fileAccept
                    // 获取id的值
                    val idValue = id.value ?: return@fileAccept
                    // 获取clickable
                    val clickableAttr = getAttribute("android:clickable", null)
                    var clickable = false
                    if (clickableAttr != null && !StringUtils.isEmpty(clickableAttr.value)) {
                        clickable = clickableAttr.value == "true"
                    }
                    if (name == "Button") {
                        clickable = true
                    }
                    // 添加到list
                    try {
                        val e = Element(name, idValue, clickable, this)
                        elements.add(e)
                    } catch (ignored: IllegalArgumentException) {
                        ignored.printStackTrace()
                    }
                }
            }
        }
    }
}
