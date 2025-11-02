package io.github.thirty30ww.defargs.intellij.util

import com.intellij.openapi.util.TextRange
import com.intellij.psi.*
import com.intellij.psi.impl.light.LightMethodBuilder
import com.intellij.psi.search.searches.ClassInheritorsSearch

/**
 * 方法分析工具类
 * 
 * 提供方法相关的分析功能
 */
object MethodAnalyzer {
    
    /**
     * 判断方法是否是抽象方法
     * 
     * 抽象方法包括：
     * 1. 显式声明为 abstract 的方法
     * 2. 接口中的非 default 方法
     * 
     * @param method 要判断的方法
     * @return true 表示是抽象方法，false 表示是具体方法
     */
    fun isAbstractMethod(method: PsiMethod): Boolean {
        // 显式声明为 abstract
        if (method.hasModifierProperty(PsiModifier.ABSTRACT)) {
            return true
        }
        
        // 接口中的非 default 方法
        if (method.containingClass?.isInterface == true && !method.hasModifierProperty(PsiModifier.DEFAULT)) {
            return true
        }
        
        return false
    }
    
    /**
     * 检查给定的索引列表是否是末尾连续的
     * 
     * 例如：
     * - 参数总数为 5，索引列表为 [3, 4]，返回 true（末尾连续）
     * - 参数总数为 5，索引列表为 [1, 2, 4]，返回 false（不连续）
     * - 参数总数为 5，索引列表为 [1, 2, 3]，返回 false（不在末尾）
     * 
     * @param indices 要检查的索引列表
     * @param totalCount 参数总数
     * @return true 表示是末尾连续的，false 表示不是
     */
    fun isTrailingContinuous(indices: List<Int>, totalCount: Int): Boolean {
        if (indices.isEmpty()) {
            return false
        }
        
        val firstIndex = indices.first()
        val expectedSize = totalCount - firstIndex
        
        // 检查数量是否匹配（是否到达末尾）
        if (indices.size != expectedSize) {
            return false
        }
        
        // 检查是否连续
        for (i in indices.indices) {
            if (indices[i] != firstIndex + i) {
                return false
            }
        }
        
        return true
    }
    
    /**
     * 构建参数列表字符串，用于错误提示
     * 
     * 格式：类型1 参数名1, 类型2 参数名2
     * 例如：int a, String name
     * 
     * @param method 方法
     * @param paramCount 参数数量（取前 N 个参数）
     * @return 参数列表字符串，如果参数数量为 0 则返回空字符串
     */
    fun buildParameterList(method: PsiMethod, paramCount: Int): String {
        if (paramCount == 0) {
            return ""
        }
        
        val params = method.parameterList.parameters.take(paramCount)
        return params.joinToString(", ") { param ->
            val typeName = param.type.presentableText
            val paramName = param.name ?: "arg"
            "$typeName $paramName"
        }
    }
    
    /**
     * 构建参数类型列表字符串，用于错误提示
     * 
     * 格式：类型1, 类型2
     * 例如：int, String
     * 
     * @param method 方法
     * @return 参数类型列表字符串
     */
    fun buildParameterTypeList(method: PsiMethod): String {
        return method.parameterList.parameters.joinToString(", ") { 
            it.type.presentableText 
        }
    }
    
    /**
     * 获取方法头的 TextRange（从修饰符到参数列表结束）
     * 不包括方法体
     * 
     * 用于在 inspection 中只高亮方法签名部分，而不是整个方法体
     * 
     * @param method 方法
     * @return 方法头的 TextRange
     */
    fun getHeaderRange(method: PsiMethod): TextRange {
        // 找到开始位置
        val startElement = method.modifierList.takeIf { it.textLength > 0 } 
            ?: method.returnTypeElement 
            ?: method.nameIdentifier
            ?: method
        
        // 结束位置是参数列表
        val endElement = method.parameterList
        
        // 计算相对于 method 的偏移
        val startOffset = startElement.textRange.startOffset - method.textRange.startOffset
        val endOffset = endElement.textRange.endOffset - method.textRange.startOffset
        
        return TextRange(startOffset, endOffset)
    }
    
    /**
     * 检查类中是否已存在指定签名的方法
     * 
     * 注意：只检查类本身的方法，不包括继承的方法，避免递归循环
     * 
     * @param containingClass 包含方法的类
     * @param methodName 方法名
     * @param parameterCount 参数数量
     * @return 如果存在返回 true，否则返回 false
     */
    fun methodExists(containingClass: com.intellij.psi.PsiClass, methodName: String, parameterCount: Int): Boolean {
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
    
    /**
     * 查找与指定签名冲突的方法
     * 
     * 在类中查找与 originalMethod 同名但参数数量为 paramCount 的方法
     * 
     * @param containingClass 包含方法的类
     * @param originalMethod 原始方法
     * @param paramCount 要查找的方法的参数数量
     * @return 冲突的方法，如果没有找到则返回 null
     */
    fun findConflictingMethod(
        containingClass: com.intellij.psi.PsiClass,
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
    
    /**
     * 查找接口/抽象方法的所有具体实现
     * 
     * @param interfaceMethod 接口或抽象类中的方法
     * @return 实现该方法的具体方法列表
     */
    fun findImplementations(interfaceMethod: PsiMethod): List<PsiMethod> {
        val results = mutableListOf<PsiMethod>()
        
        // 使用 PsiMethod 的内置方法查找所有实现
        val superMethodHierarchy = interfaceMethod.findDeepestSuperMethods()
        if (superMethodHierarchy.isEmpty()) {
            // 如果没有更深的父方法，就从当前方法开始查找
            findImplementationsRecursive(interfaceMethod, results)
        } else {
            // 从最深的父方法开始查找
            for (superMethod in superMethodHierarchy) {
                findImplementationsRecursive(superMethod, results)
            }
        }
        
        return results
    }
    
    /**
     * 递归查找方法的实现
     * 
     * @param method 要查找实现的方法
     * @param results 结果列表
     */
    private fun findImplementationsRecursive(method: PsiMethod, results: MutableList<PsiMethod>) {
        // 查找直接覆盖/实现此方法的子方法
        val containingClass = method.containingClass ?: return
        
        // 遍历所有可能的子类
        val inheritors = findInheritors(containingClass)
        for (inheritor in inheritors) {
            // 在子类中查找覆盖此方法的方法
            val overridingMethod = findOverridingMethod(inheritor, method)
            if (overridingMethod != null) {
                // 如果是具体实现（不是抽象的），添加到结果
                if (!overridingMethod.hasModifierProperty(PsiModifier.ABSTRACT)) {
                    results.add(overridingMethod)
                } else {
                    // 如果还是抽象的，继续递归查找
                    findImplementationsRecursive(overridingMethod, results)
                }
            }
        }
    }
    
    /**
     * 查找类的所有子类/实现类
     * 
     * @param psiClass 父类/接口
     * @return 子类/实现类列表
     */
    private fun findInheritors(psiClass: PsiClass): List<PsiClass> {
        val results = mutableListOf<PsiClass>()
        
        // 使用 IntelliJ 提供的高效搜索 API
        ClassInheritorsSearch.search(psiClass, psiClass.resolveScope, true).forEach { inheritor ->
            results.add(inheritor)
        }
        
        return results
    }
    
    /**
     * 在子类中查找覆盖指定方法的方法
     * 
     * @param inheritor 子类
     * @param superMethod 父类方法
     * @return 覆盖方法，如果没有则返回 null
     */
    private fun findOverridingMethod(inheritor: PsiClass, superMethod: PsiMethod): PsiMethod? {
        val methods = inheritor.findMethodsByName(superMethod.name, false)
        for (method in methods) {
            // 检查是否是覆盖方法
            if (method.findSuperMethods().any { it == superMethod || it.isEquivalentTo(superMethod) }) {
                return method
            }
        }
        return null
    }
    
    /**
     * 在类中查找虚拟重载方法
     * 
     * @param psiClass 要搜索的类
     * @param methodName 方法名
     * @param paramCount 参数数量
     * @param originalMethod 原始方法（用于匹配虚拟方法的导航目标）
     * @return 虚拟方法，如果没有则返回 null
     */
    fun findVirtualMethod(
        psiClass: PsiClass,
        methodName: String,
        paramCount: Int,
        originalMethod: PsiMethod
    ): PsiMethod? {
        // 查找类中的所有方法，包括虚拟方法
        val allMethods = psiClass.allMethods
        
        for (method in allMethods) {
            // 检查方法名和参数数量
            if (method.name == methodName && 
                method.parameterList.parametersCount == paramCount) {
                
                // 如果是虚拟方法（LightMethodBuilder），且导航目标是原始方法
                if (method is LightMethodBuilder) {
                    if (method.navigationElement == originalMethod) {
                        return method
                    }
                } else {
                    // 如果是实际方法（参数较少的手动实现），也返回
                    return method
                }
            }
        }
        
        return null
    }
    
    /**
     * 在类中查找手动定义的重载方法
     * 
     * @param psiClass 要搜索的类
     * @param methodName 方法名
     * @param paramCount 参数数量
     * @param referenceMethod 参考方法（用于检查签名兼容性）
     * @return 重载方法，如果没有则返回 null
     */
    fun findOverloadMethod(
        psiClass: PsiClass,
        methodName: String,
        paramCount: Int,
        referenceMethod: PsiMethod
    ): PsiMethod? {
        val methods = psiClass.findMethodsByName(methodName, false)
        
        return methods.find { method ->
            method.parameterList.parametersCount == paramCount &&
            !method.hasModifierProperty(PsiModifier.STATIC) &&
            isSignatureCompatible(referenceMethod, method, paramCount)
        }
    }
    
    /**
     * 检查两个方法的签名是否兼容，
     *
     * 即方法名相同，且前 compareParamCount 个参数类型兼容。
     *
     * @param method1 方法1
     * @param method2 方法2
     * @param compareParamCount 需要比较的参数数量
     * @return true 如果签名兼容
     */
    private fun isSignatureCompatible(
        method1: PsiMethod,
        method2: PsiMethod,
        compareParamCount: Int
    ): Boolean {
        if (method1.name != method2.name) return false
        
        val params1 = method1.parameterList.parameters
        val params2 = method2.parameterList.parameters
        
        if (params2.size < compareParamCount) return false
        
        // 检查前 N 个参数类型是否兼容
        for (i in 0 until compareParamCount) {
            val type1 = params1[i].type
            val type2 = params2[i].type
            
            if (!type1.isAssignableFrom(type2) && !type2.isAssignableFrom(type1)) {
                return false
            }
        }
        
        return true
    }
}

