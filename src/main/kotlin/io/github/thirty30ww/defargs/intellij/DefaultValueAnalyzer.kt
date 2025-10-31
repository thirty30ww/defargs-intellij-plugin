package io.github.thirty30ww.defargs.intellij

import com.intellij.psi.*

/**
 * 分析 @DefaultValue 注解并计算需要生成的重载方法信息
 */
object DefaultValueAnalyzer {
    
    /**
     * DefaultValue 注解的全限定名
     * 如果注解的包名改变，只需要修改这里即可
     */
    private const val DEFAULT_VALUE_ANNOTATION = "io.github.thirty30ww.defargs.annotation.DefaultValue"
    
    /**
     * 分析方法的参数，返回所有需要生成的重载方法的参数数量列表，例如
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
     * @param method 要分析的方法的 PsiMethod 对象
     * @return 所有需要生成的重载方法的参数数量列表
     */
    fun analyzeMethod(method: PsiMethod): List<Int> {
        // 获取方法的所有参数（PSI 树节点）
        val parameters = method.parameterList.parameters
        // 无参方法，不需要处理
        if (parameters.isEmpty()) return emptyList()
        
        // 找出所有带 @DefaultValue 注解的参数位置
        val defaultValueIndices = mutableListOf<Int>()
        parameters.forEachIndexed { index, param ->
            if (hasDefaultValueAnnotation(param)) {
                defaultValueIndices.add(index)
            }
        }
        
        if (defaultValueIndices.isEmpty()) {
            return emptyList()
        }
        
        // 检查是否是末尾连续的默认值参数（目前只支持这种情况）
        val firstDefaultIndex = defaultValueIndices.first()
        val isTrailing = defaultValueIndices.size == (parameters.size - firstDefaultIndex)
        
        if (!isTrailing) {
            // 不是末尾连续的默认值参数，不生成重载
            return emptyList()
        }
        
        // 生成重载方法的参数数量列表
        // 例如：方法有 3 个参数，后 2 个有默认值，则生成参数数量为 [2, 1] 的重载
        val result = mutableListOf<Int>()
        for (i in 1..defaultValueIndices.size) {
            result.add(parameters.size - i)
        }
        
        return result
    }
    
    /**
     * 检查参数是否有 @DefaultValue 注解
     */
    private fun hasDefaultValueAnnotation(parameter: PsiParameter): Boolean {
        return parameter.annotations.any { 
            it.qualifiedName == DEFAULT_VALUE_ANNOTATION
        }
    }
    
    /**
     * 获取参数的默认值（从注解中）
     */
    fun getDefaultValue(parameter: PsiParameter): String? {
        val annotation = parameter.annotations.find { 
            it.qualifiedName == DEFAULT_VALUE_ANNOTATION
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


