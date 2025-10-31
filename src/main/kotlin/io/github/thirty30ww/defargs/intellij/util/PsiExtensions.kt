package io.github.thirty30ww.defargs.intellij.util

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiMethod

/**
 * PSI 扩展方法
 * 提供 PSI 元素相关的辅助功能
 */

/**
 * 获取方法头的 TextRange（从修饰符到参数列表结束）
 * 不包括方法体
 */
fun PsiMethod.getHeaderRange(): TextRange {
    // 找到开始位置
    val startElement = this.modifierList.takeIf { it.textLength > 0 } 
        ?: this.returnTypeElement 
        ?: this.nameIdentifier
        ?: this
    
    // 结束位置是参数列表
    val endElement = this.parameterList
    
    // 计算相对于 method 的偏移
    val startOffset = startElement.textRange.startOffset - this.textRange.startOffset
    val endOffset = endElement.textRange.endOffset - this.textRange.startOffset
    
    return TextRange(startOffset, endOffset)
}

/**
 * 获取参数类型名称列表（用于错误信息）
 */
fun PsiMethod.getParameterTypeNames(paramCount: Int): String {
    val params = this.parameterList.parameters.take(paramCount)
    return params.joinToString(", ") { it.type.presentableText }
}

