package io.github.thirty30ww.defargs.intellij.util

import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiJavaFile

/**
 * Import 语句辅助工具类
 * 
 * 提供统一的导包逻辑，避免重复导入
 */
object ImportHelper {
    
    /**
     * 为指定文件添加类的 import 语句（如果尚未导入）
     * 
     * @param project 当前项目
     * @param context 用于获取 containingFile 和 resolveScope 的上下文元素
     * @param qualifiedClassName 要导入的类的全限定名（如 "io.github.thirty30ww.defargs.DefaultValue"）
     */
    fun addImportIfNeeded(project: Project, context: PsiElement, qualifiedClassName: String) {
        val containingFile = context.containingFile
        if (containingFile !is PsiJavaFile) {
            return
        }
        
        val importList = containingFile.importList ?: return
        
        // 检查是否已经导入了该类
        val alreadyImported = importList.importStatements.any { 
            it.qualifiedName == qualifiedClassName 
        }
        
        if (alreadyImported) {
            return
        }
        
        // 查找要导入的类
        val classToImport = JavaPsiFacade.getInstance(project).findClass(
            qualifiedClassName,
            context.resolveScope
        ) ?: return
        
        // 创建并添加 import 语句
        val factory = JavaPsiFacade.getElementFactory(project)
        val importStatement = factory.createImportStatement(classToImport)
        importList.add(importStatement)
    }
}

