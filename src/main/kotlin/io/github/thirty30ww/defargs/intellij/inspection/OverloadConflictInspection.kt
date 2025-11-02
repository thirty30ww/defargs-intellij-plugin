package io.github.thirty30ww.defargs.intellij.inspection

import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.*
import io.github.thirty30ww.defargs.intellij.util.AnnotationAnalyzer
import io.github.thirty30ww.defargs.intellij.util.MessageBuilder
import io.github.thirty30ww.defargs.intellij.util.MethodAnalyzer

/**
 * 检测 @DefaultValue 和 @Omittable 生成的重载方法与手动定义的方法冲突
 */
class OverloadConflictInspection : AbstractBaseJavaLocalInspectionTool() {

    /**
     * 检查方法调用冲突
     *
     * 检查调用带有 @DefaultValue/@Omittable 的方法时是否与手动定义的方法冲突
     */
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return object : JavaElementVisitor() {

            override fun visitMethod(method: PsiMethod) {
                super.visitMethod(method)
                checkMethodDefinitionConflict(method, holder)
            }

            override fun visitMethodCallExpression(expression: PsiMethodCallExpression) {
                super.visitMethodCallExpression(expression)
                checkMethodCallConflict(expression, holder)
            }
        }
    }
    
    /**
     * 检查方法定义冲突
     * 
     * 检查带有 @DefaultValue/@Omittable 的方法生成的重载是否与已存在的方法冲突
     * 
     * @param method 要检查的方法
     * @param holder 问题报告器
     */
    private fun checkMethodDefinitionConflict(method: PsiMethod, holder: ProblemsHolder) {
        if (method.isConstructor) return
        val containingClass = method.containingClass ?: return
        
        // 分析方法会生成哪些重载
        val overloadParameterCounts = AnnotationAnalyzer.analyzeMethod(method)
        
        // 检查每个重载是否与已存在的方法冲突
        for (paramCount in overloadParameterCounts) {
            checkOverloadConflict(method, containingClass, paramCount, holder)
        }
    }
    
    /**
     * 检查单个重载方法的冲突
     * 
     * @param method 原始方法
     * @param containingClass 包含该方法的类
     * @param paramCount 重载方法的参数数量
     * @param holder 问题报告器
     */
    private fun checkOverloadConflict(
        method: PsiMethod,
        containingClass: PsiClass,
        paramCount: Int,
        holder: ProblemsHolder
    ) {
        // 检查是否存在同名同参数数量的方法
        if (!MethodAnalyzer.methodExists(containingClass, method.name, paramCount)) {
            return
        }
        
        // 找到冲突的方法
        val conflictingMethod = MethodAnalyzer.findConflictingMethod(containingClass, method, paramCount) ?: return
        
        // 报告冲突
        reportMethodConflict(method, conflictingMethod, paramCount, containingClass, holder)
    }
    
    /**
     * 报告方法定义冲突
     * 
     * @param originalMethod 带有注解的原始方法
     * @param conflictingMethod 与生成的重载冲突的方法
     * @param paramCount 重载方法的参数数量
     * @param containingClass 包含这些方法的类
     * @param holder 问题报告器
     */
    private fun reportMethodConflict(
        originalMethod: PsiMethod,
        conflictingMethod: PsiMethod,
        paramCount: Int,
        containingClass: PsiClass,
        holder: ProblemsHolder
    ) {
        val className = containingClass.qualifiedName ?: "Unknown"
        val paramTypes = MethodAnalyzer.buildParameterList(originalMethod, paramCount)
        
        // 在原方法上报告错误 - 只高亮方法头
        holder.registerProblem(
            originalMethod,
            MethodAnalyzer.getHeaderRange(originalMethod),
            MessageBuilder.methodAlreadyDefined(className, originalMethod.name, paramTypes)
        )
        
        // 也在冲突的方法上报告错误 - 只高亮方法头
        holder.registerProblem(
            conflictingMethod,
            MethodAnalyzer.getHeaderRange(conflictingMethod),
            MessageBuilder.methodConflictsWithDefaultValue()
        )
    }
    
    /**
     * 检查方法调用冲突
     * 
     * 检查方法调用是否因为重载而产生歧义
     * 
     * @param expression 方法调用表达式
     * @param holder 问题报告器
     */
    private fun checkMethodCallConflict(expression: PsiMethodCallExpression, holder: ProblemsHolder) {
        val calledMethod = expression.resolveMethod() ?: return
        val conflictInfo = getConflictInfo(calledMethod) ?: return
        
        val (className, methodName, paramTypes) = conflictInfo
        holder.registerProblem(
            expression.argumentList,
            MessageBuilder.methodCallAmbiguous(className, methodName, paramTypes)
        )
    }
    
    /**
     * 获取冲突信息
     * 
     * 只有手动定义的冲突方法才返回信息，生成的虚拟方法不算冲突
     * 
     * @param method 要检查的方法
     * @return Triple(类名, 方法名, 参数类型) 或 null（如果没有冲突）
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
                val paramTypes = MethodAnalyzer.buildParameterTypeList(method)
                return Triple(className, methodName, paramTypes)
            }
        }
        
        return null
    }
}

