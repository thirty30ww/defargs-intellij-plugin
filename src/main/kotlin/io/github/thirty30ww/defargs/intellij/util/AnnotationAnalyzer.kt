package io.github.thirty30ww.defargs.intellij.util

import com.intellij.psi.*
import io.github.thirty30ww.defargs.intellij.constant.DefArgsConstants

/**
 * 注解分析工具类
 * 
 * 负责分析 @DefaultValue 和 @Omittable 注解并计算需要生成的重载方法信息
 */
object AnnotationAnalyzer {
    
    /**
     * 分析方法的参数，返回所有需要生成的重载方法的参数数量列表
     *
     * 支持两种注解：
     * - @DefaultValue：用于具体方法（有方法体）
     * - @Omittable：用于抽象方法（接口或抽象类）
     *
     * 假设有方法
     * ```
     * void func(int a, @DefaultValue("2") int b, @DefaultValue("3") int c)
     * ```
     *
     * 则会生成参数数量为 [2, 1] 的两个重载方法：
     * ```
     * void func(int a, int b)
     * void func(int a)
     * ```
     *
     * 所以返回
     * ```
     * [2, 1]
     * ```
     *
     * 注意：如果注解使用不正确（例如抽象方法使用 @DefaultValue 或具体方法使用 @Omittable），
     * 则不生成虚拟重载方法，只由 AnnotationUsageInspection 报错。
     *
     * @param method 要分析的方法的 PsiMethod 对象
     * @return 所有需要生成的重载方法的参数数量列表
     */
    fun analyzeMethod(method: PsiMethod): List<Int> {
        val parameters = method.parameterList.parameters    // 获取方法的所有参数（PSI 树节点）
        val isAbstractMethod = MethodAnalyzer.isAbstractMethod(method)  // 判断方法是否是抽象方法

        // 无参方法，不需要处理
        if (parameters.isEmpty()) return emptyList()
        
        // 找出所有带正确注解的参数位置
        // @DefaultValue 只用于具体方法，@Omittable 只用于抽象方法
        val annotatedIndices = mutableListOf<Int>()
        parameters.forEachIndexed { index, param ->
            // 如果参数有注解且使用正确，添加到列表
            if (hasAnnotation(param) && isAnnotationUsageValid(param, isAbstractMethod)) {
                annotatedIndices.add(index)
            }
        }
        
        if (annotatedIndices.isEmpty()) {
            return emptyList()
        }
        
        // 检查是否是末尾连续的可省略参数（目前只支持这种情况）
        if (!MethodAnalyzer.isTrailingContinuous(annotatedIndices, parameters.size)) {
            // 不是末尾连续的可省略参数，不生成重载
            return emptyList()
        }
        
        // 生成重载方法的参数数量列表
        // 例如：方法有 3 个参数，后 2 个有注解，则生成参数数量为 [2, 1] 的重载
        val result = mutableListOf<Int>()
        for (i in 1..annotatedIndices.size) {
            result.add(parameters.size - i)
        }
        
        return result
    }
    
    /**
     * 检查参数是否同时使用了 @DefaultValue 和 @Omittable 注解
     */
    fun hasBothAnnotations(parameter: PsiParameter): Boolean {
        return hasDefaultValueAnnotation(parameter) && hasOmittableAnnotation(parameter)
    }
    
    /**
     * 检查参数是否在抽象方法上错误地使用了 @DefaultValue 注解
     */
    fun hasDefaultValueOnAbstractMethod(parameter: PsiParameter, isAbstractMethod: Boolean): Boolean {
        return hasDefaultValueAnnotation(parameter) && isAbstractMethod
    }
    
    /**
     * 检查参数是否在具体方法上错误地使用了 @Omittable 注解
     */
    fun hasOmittableOnConcreteMethod(parameter: PsiParameter, isAbstractMethod: Boolean): Boolean {
        return hasOmittableAnnotation(parameter) && !isAbstractMethod
    }
    
    /**
     * 验证参数注解使用是否正确
     * 
     * @param parameter 要验证的参数
     * @param isAbstractMethod 参数所在的方法是否是抽象方法
     * @return true 表示注解使用正确，false 表示注解使用错误
     */
    private fun isAnnotationUsageValid(parameter: PsiParameter, isAbstractMethod: Boolean): Boolean {
        // 检查互斥：不能同时使用两个注解
        if (hasBothAnnotations(parameter)) {
            return false
        }
        
        // @DefaultValue 只能用于具体方法
        if (hasDefaultValueOnAbstractMethod(parameter, isAbstractMethod)) {
            return false
        }
        
        // @Omittable 只能用于抽象方法
        if (hasOmittableOnConcreteMethod(parameter, isAbstractMethod)) {
            return false
        }
        
        return true
    }
    
    /**
     * 检查参数是否有指定的注解
     */
    private fun hasAnnotation(parameter: PsiParameter, qualifiedName: String): Boolean {
        return parameter.annotations.any { it.qualifiedName == qualifiedName }
    }

    /**
     * 检查参数是否有 @DefaultValue 或 @Omittable 注解
     */
    private fun hasAnnotation(parameter: PsiParameter): Boolean {
        return hasDefaultValueAnnotation(parameter) || hasOmittableAnnotation(parameter)
    }
    
    /**
     * 查找参数上的指定注解
     */
    fun findAnnotation(parameter: PsiParameter, qualifiedName: String): PsiAnnotation? {
        return parameter.annotations.find { it.qualifiedName == qualifiedName }
    }
    
    /**
     * 检查参数是否有 @DefaultValue 注解
     */
    fun hasDefaultValueAnnotation(parameter: PsiParameter): Boolean {
        return hasAnnotation(parameter, DefArgsConstants.DEFAULT_VALUE_ANNOTATION)
    }
    
    /**
     * 检查参数是否有 @Omittable 注解
     */
    fun hasOmittableAnnotation(parameter: PsiParameter): Boolean {
        return hasAnnotation(parameter, DefArgsConstants.OMITTABLE_ANNOTATION)
    }
    
    /**
     * 获取参数的默认值（从 @DefaultValue 注解中）
     * 注意：@Omittable 注解没有值
     */
    fun getDefaultValue(parameter: PsiParameter): String? {
        val annotation = parameter.annotations.find { 
            it.qualifiedName == DefArgsConstants.DEFAULT_VALUE_ANNOTATION
        } ?: return null
        
        val value = annotation.findAttributeValue("value")
        if (value is PsiLiteralExpression) {
            return value.value?.toString()
        }
        
        return null
    }
    
    /**
     * 检查类中是否已存在指定签名的方法
     * 注意：只检查类本身的方法，不包括继承的方法，避免递归循环
     */
    fun methodExists(containingClass: PsiClass, methodName: String, parameterCount: Int): Boolean {
        // 使用 ownMethods 而不是 findMethodsByName，避免触发 PsiAugmentProvider 导致递归
        if (containingClass is com.intellij.psi.impl.source.PsiExtensibleClass) {
            return containingClass.ownMethods.any {
                it.name == methodName && it.parameterList.parametersCount == parameterCount
            }
        }
        
        // 对于其他类型的 PsiClass，回退到 methods 属性
        return containingClass.methods.any {
            it.name == methodName && it.parameterList.parametersCount == parameterCount
        }
    }
}

