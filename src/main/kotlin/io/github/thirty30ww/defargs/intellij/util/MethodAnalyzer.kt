package io.github.thirty30ww.defargs.intellij.util

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiModifier

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
}

