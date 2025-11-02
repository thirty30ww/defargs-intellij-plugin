package io.github.thirty30ww.defargs.intellij.inspection

import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.*
import io.github.thirty30ww.defargs.intellij.util.AnnotationAnalyzer
import io.github.thirty30ww.defargs.intellij.util.MessageBuilder
import io.github.thirty30ww.defargs.intellij.util.getHeaderRange
import io.github.thirty30ww.defargs.intellij.util.getParameterTypeNames

/**
 * 检测 @DefaultValue 生成的重载方法与手动定义的方法冲突
 */
class OverloadConflictInspection : AbstractBaseJavaLocalInspectionTool() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return object : JavaElementVisitor() {

            override fun visitMethod(method: PsiMethod) {
                super.visitMethod(method)
                
                if (method.isConstructor) return
                val containingClass = method.containingClass ?: return
                
                // 分析方法，查看会生成哪些重载
                val overloadParameterCounts = AnnotationAnalyzer.analyzeMethod(method)
                
                // 检查每个会生成的重载是否与已存在的方法冲突
                for (paramCount in overloadParameterCounts) {
                    if (AnnotationAnalyzer.methodExists(containingClass, method.name, paramCount)) {
                        val conflictingMethod = findConflictingMethod(containingClass, method, paramCount)
                        
                        if (conflictingMethod != null) {
                            val className = containingClass.qualifiedName ?: "Unknown"
                            val paramTypes = method.getParameterTypeNames(paramCount)
                            
                            // 在原方法上报告错误 - 只高亮方法头
                            holder.registerProblem(
                                method,
                                method.getHeaderRange(),
                                MessageBuilder.methodAlreadyDefined(className, method.name, paramTypes)
                            )
                            
                            // 也在冲突的方法上报告错误 - 只高亮方法头
                            holder.registerProblem(
                                conflictingMethod,
                                conflictingMethod.getHeaderRange(),
                                MessageBuilder.methodConflictsWithDefaultValue()
                            )
                        }
                    }
                }
            }

            override fun visitMethodCallExpression(expression: PsiMethodCallExpression) {
                super.visitMethodCallExpression(expression)
                
                val calledMethod = expression.resolveMethod() ?: return
                val conflictInfo = getConflictInfo(calledMethod)
                
                if (conflictInfo != null) {
                    val (className, methodName, paramTypes) = conflictInfo
                    holder.registerProblem(
                        expression.argumentList,
                        MessageBuilder.methodCallAmbiguous(className, methodName, paramTypes)
                    )
                }
            }
        }
    }
    
    /**
     * 获取冲突信息
     * 返回 Triple(类名, 方法名, 参数类型) 或 null
     * 
     * 只有手动定义的冲突方法才返回信息，生成的虚拟方法不算冲突
     */
    private fun getConflictInfo(method: PsiMethod): Triple<String, String, String>? {
        val containingClass = method.containingClass ?: return null
        
        // 如果是虚拟方法（由插件生成的），不是冲突方法
        if (method is com.intellij.psi.impl.light.LightElement) return null
        
        // 如果当前方法本身就带有 @DefaultValue，不是冲突方法
        if (AnnotationAnalyzer.analyzeMethod(method).isNotEmpty()) return null
        
        // 查找类中所有带 @DefaultValue 的方法
        val methods = if (containingClass is com.intellij.psi.impl.source.PsiExtensibleClass) {
            containingClass.ownMethods
        } else {
            containingClass.methods.toList()
        }
        
        val currentParamCount = method.parameterList.parametersCount
        
        for (otherMethod in methods) {
            if (otherMethod.isConstructor || otherMethod == method) continue
            
            // 分析该方法会生成哪些重载
            val overloadParameterCounts = AnnotationAnalyzer.analyzeMethod(otherMethod)
            
            // 检查是否会生成与当前方法签名相同的重载
            if (overloadParameterCounts.contains(currentParamCount) && method.name == otherMethod.name) {
                val className = containingClass.name ?: "Unknown"
                val methodName = method.name
                val paramTypes = method.parameterList.parameters.joinToString(", ") { 
                    it.type.presentableText 
                }
                return Triple(className, methodName, paramTypes)
            }
        }
        
        return null
    }
    
    /**
     * 查找与生成方法冲突的实际方法
     */
    private fun findConflictingMethod(
        containingClass: PsiClass,
        originalMethod: PsiMethod,
        paramCount: Int
    ): PsiMethod? {
        val methods = if (containingClass is com.intellij.psi.impl.source.PsiExtensibleClass) {
            containingClass.ownMethods
        } else {
            containingClass.methods.toList()
        }
        
        return methods.find {
            it != originalMethod &&
            it.name == originalMethod.name &&
            it.parameterList.parametersCount == paramCount
        }
    }
}

