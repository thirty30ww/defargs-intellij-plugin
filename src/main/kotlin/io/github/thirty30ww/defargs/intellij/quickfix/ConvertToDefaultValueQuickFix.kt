package io.github.thirty30ww.defargs.intellij.quickfix

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiAnnotation
import io.github.thirty30ww.defargs.intellij.constant.DefArgsConstants
import io.github.thirty30ww.defargs.intellij.util.ImportHelper

/**
 * 快速修复：将 @Omittable 转换为 @DefaultValue
 * 
 * 当在具体方法上错误地使用了 @Omittable 时，
 * 提供快速修复选项将其转换为 @DefaultValue
 */
class ConvertToDefaultValueQuickFix : LocalQuickFix {
    
    override fun getFamilyName(): String = "将 @Omittable 转换为 @DefaultValue"
    
    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val annotation = descriptor.psiElement as? PsiAnnotation ?: return
        
        // 创建新的 @DefaultValue 注解（带默认值 ""）
        val factory = JavaPsiFacade.getElementFactory(project)
        val defaultValueAnnotation = factory.createAnnotationFromText(
            "@${DefArgsConstants.DEFAULT_VALUE_ANNOTATION.substringAfterLast('.')}(\"\")",
            annotation
        )
        
        // 添加 import 语句（如果尚未导入）
        ImportHelper.addImportIfNeeded(project, annotation, DefArgsConstants.DEFAULT_VALUE_ANNOTATION)
        
        // 替换注解
        annotation.replace(defaultValueAnnotation)
    }
}

