package io.github.thirty30ww.defargs.intellij.quickfix

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import io.github.thirty30ww.defargs.intellij.constant.DefArgsConstants
import io.github.thirty30ww.defargs.intellij.util.AnnotationAnalyzer
import io.github.thirty30ww.defargs.intellij.util.ImportHelper

/**
 * 快速修复：在父方法的对应参数上添加 @Omittable 或将 @DefaultValue 转换为 @Omittable
 */
class AddOmittableToSuperMethodQuickFix(
    private val superMethod: PsiMethod,
    private val paramIndex: Int
) : LocalQuickFix {
    
    override fun getFamilyName(): String = "在父方法的参数上添加 @Omittable"
    
    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        if (paramIndex >= superMethod.parameterList.parametersCount) {
            return
        }
        
        val superParam = superMethod.parameterList.parameters[paramIndex]
        val factory = JavaPsiFacade.getElementFactory(project)
        
        // 检查参数是否已有 @DefaultValue 注解
        val existingDefaultValue = AnnotationAnalyzer.findAnnotation(
            superParam,
            DefArgsConstants.DEFAULT_VALUE_ANNOTATION
        )
        
        if (existingDefaultValue != null) {
            // 情况 1：父方法参数有 @DefaultValue，转换为 @Omittable
            val omittableAnnotation = factory.createAnnotationFromText(
                "@${DefArgsConstants.OMITTABLE_ANNOTATION.substringAfterLast('.')}",
                superParam
            )
            existingDefaultValue.replace(omittableAnnotation)
        } else {
            // 情况 2：父方法参数没有注解，添加 @Omittable
            val modifierList = superParam.modifierList ?: return
            val omittableAnnotation = factory.createAnnotationFromText(
                "@${DefArgsConstants.OMITTABLE_ANNOTATION.substringAfterLast('.')}",
                superParam
            )
            modifierList.addBefore(omittableAnnotation, modifierList.firstChild)
        }
        
        // 添加 import 语句（如果尚未导入）
        ImportHelper.addImportIfNeeded(project, superMethod, DefArgsConstants.OMITTABLE_ANNOTATION)
    }
}

