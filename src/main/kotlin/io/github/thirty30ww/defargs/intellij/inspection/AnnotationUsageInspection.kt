package io.github.thirty30ww.defargs.intellij.inspection

import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.*
import io.github.thirty30ww.defargs.intellij.constant.DefArgsConstants
import io.github.thirty30ww.defargs.intellij.util.AnnotationAnalyzer
import io.github.thirty30ww.defargs.intellij.util.MessageBuilder
import io.github.thirty30ww.defargs.intellij.util.MethodAnalyzer

/**
 * 检查 @DefaultValue 和 @Omittable 注解的使用是否正确
 * 
 * 规则：
 * - @DefaultValue 只能用于具体方法（有方法体的方法）
 * - @Omittable 只能用于抽象方法（接口或抽象类的抽象方法）
 * - 同一个参数不能同时使用两个注解
 */
class AnnotationUsageInspection : AbstractBaseJavaLocalInspectionTool() {
    
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return object : JavaElementVisitor() {
            override fun visitMethod(method: PsiMethod) {
                super.visitMethod(method)
                
                // 判断方法是否是抽象方法
                val isAbstractMethod = MethodAnalyzer.isAbstractMethod(method)
                
                // 检查每个参数
                for (parameter in method.parameterList.parameters) {
                    checkBothAnnotations(parameter, holder)
                    checkDefaultValueOnAbstractMethod(parameter, isAbstractMethod, holder)
                    checkOmittableOnConcreteMethod(parameter, isAbstractMethod, holder)
                }
            }
        }
    }
    
    /**
     * 检查参数是否同时使用了两个注解
     */
    private fun checkBothAnnotations(parameter: PsiParameter, holder: ProblemsHolder) {
        if (!AnnotationAnalyzer.hasBothAnnotations(parameter)) {
            return
        }
        
        val defaultValueAnnotation = AnnotationAnalyzer.findAnnotation(parameter, DefArgsConstants.DEFAULT_VALUE_ANNOTATION)
        val omittableAnnotation = AnnotationAnalyzer.findAnnotation(parameter, DefArgsConstants.OMITTABLE_ANNOTATION)
        
        val errorMessage = MessageBuilder.annotationsMutuallyExclusive(parameter.name ?: "")
        
        if (defaultValueAnnotation != null) {
            holder.registerProblem(defaultValueAnnotation, errorMessage)
        }
        if (omittableAnnotation != null) {
            holder.registerProblem(omittableAnnotation, errorMessage)
        }
    }
    
    /**
     * 检查是否在抽象方法上错误地使用了 @DefaultValue
     */
    private fun checkDefaultValueOnAbstractMethod(parameter: PsiParameter, isAbstractMethod: Boolean, holder: ProblemsHolder) {
        if (!AnnotationAnalyzer.hasDefaultValueOnAbstractMethod(parameter, isAbstractMethod)) {
            return
        }
        
        val annotation = AnnotationAnalyzer.findAnnotation(parameter, DefArgsConstants.DEFAULT_VALUE_ANNOTATION)
        if (annotation != null) {
            holder.registerProblem(
                annotation,
                MessageBuilder.defaultValueOnAbstractMethod()
            )
        }
    }
    
    /**
     * 检查是否在具体方法上错误地使用了 @Omittable
     */
    private fun checkOmittableOnConcreteMethod(parameter: PsiParameter, isAbstractMethod: Boolean, holder: ProblemsHolder) {
        if (!AnnotationAnalyzer.hasOmittableOnConcreteMethod(parameter, isAbstractMethod)) {
            return
        }
        
        val annotation = AnnotationAnalyzer.findAnnotation(parameter, DefArgsConstants.OMITTABLE_ANNOTATION)
        if (annotation != null) {
            holder.registerProblem(
                annotation,
                MessageBuilder.omittableOnConcreteMethod()
            )
        }
    }
}
