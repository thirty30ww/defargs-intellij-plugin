package io.github.thirty30ww.defargs.intellij.search

import com.intellij.openapi.application.QueryExecutorBase
import com.intellij.psi.*
import com.intellij.psi.impl.light.LightMethodBuilder
import com.intellij.psi.search.PsiSearchHelper
import com.intellij.psi.search.UsageSearchContext
import com.intellij.psi.search.searches.MethodReferencesSearch
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.Processor
import io.github.thirty30ww.defargs.intellij.util.AnnotationAnalyzer

/**
 * 虚拟方法引用搜索器
 * 
 * 作用：让 IntelliJ 在查找引用时，能够找到对虚拟重载方法的调用
 * 
 * 工作原理：
 * 1. 当用户在方法定义上执行"查找引用"时，此搜索器会被调用
 * 2. 检查方法是否有 @DefaultValue 或 @Omittable 注解的参数
 * 3. 如果有，搜索代码中所有对该方法名的调用
 * 4. 过滤出参数数量匹配虚拟重载方法的调用
 * 5. 将这些调用添加到搜索结果中
 * 
 * 示例：
 * ```
 * // 方法定义
 * public void test(String a, @DefaultValue("b") String b) { ... }
 * 
 * // 调用处
 * obj.test("a");        // 1个参数，调用虚拟方法 - 会被找到 ✓
 * obj.test("a", "b");   // 2个参数，调用原方法 - 默认就能找到 ✓
 * ```
 */
class ReferenceSearcher : QueryExecutorBase<PsiReference, MethodReferencesSearch.SearchParameters>(true) {

    /**
     * 处理查询，查找对虚拟重载方法的调用
     * @param queryParameters 搜索参数，包含要查找引用的方法
     * @param consumer 结果处理器，用于将找到的引用添加到搜索结果中
     */
    override fun processQuery(
        queryParameters: MethodReferencesSearch.SearchParameters,
        consumer: Processor<in PsiReference>
    ) {
        val method = queryParameters.method
        
        // 只处理普通方法（跳过构造函数）
        if (method.isConstructor) return
        
        // 分析方法，获取虚拟重载方法的参数数量列表
        // 例如：方法有3个参数，后2个有默认值，则返回 [1, 2]（表示生成了1个参数和2个参数的重载）
        val virtualOverloadParamCounts = AnnotationAnalyzer.analyzeMethod(method)
        
        // 如果没有默认值参数，无需处理
        if (virtualOverloadParamCounts.isEmpty()) return
        
        // 搜索所有对该方法名的调用
        searchMethodCalls(method, virtualOverloadParamCounts, queryParameters, consumer)
    }
    
    /**
     * 搜索方法调用，找出调用虚拟重载方法的地方
     * 
     * @param method 原方法
     * @param virtualParamCounts 虚拟重载方法的参数数量列表
     * @param queryParameters 搜索参数
     * @param consumer 结果处理器
     */
    private fun searchMethodCalls(
        method: PsiMethod,
        virtualParamCounts: List<Int>,
        queryParameters: MethodReferencesSearch.SearchParameters,
        consumer: Processor<in PsiReference>
    ) {
        val searchHelper = PsiSearchHelper.getInstance(method.project)
        val searchScope = queryParameters.effectiveSearchScope
        
        // 在代码中搜索所有包含方法名的地方
        searchHelper.processElementsWithWord(
            { element, _ ->
                processMethodCallCandidate(element, method, virtualParamCounts, consumer)
                true // 继续搜索
            },
            searchScope,
            method.name,
            UsageSearchContext.IN_CODE,
            true
        )
    }
    
    /**
     * 处理可能的方法调用，判断是否是对虚拟重载方法的调用
     * 
     * @param element 包含方法名的 PSI 元素
     * @param originalMethod 原方法
     * @param virtualParamCounts 虚拟重载方法的参数数量列表
     * @param consumer 结果处理器
     */
    private fun processMethodCallCandidate(
        element: PsiElement,
        originalMethod: PsiMethod,
        virtualParamCounts: List<Int>,
        consumer: Processor<in PsiReference>
    ) {
        // 查找包含该元素的方法调用表达式
        val callExpression = PsiTreeUtil.getParentOfType(element, PsiMethodCallExpression::class.java) 
            ?: return
        
        val methodExpression = callExpression.methodExpression
        
        // 检查调用的方法名是否匹配
        if (methodExpression.referenceName != originalMethod.name) return
        
        // 获取调用时传入的参数数量
        val actualParamCount = callExpression.argumentList.expressionCount
        
        // 只处理参数数量匹配虚拟重载方法的调用
        if (actualParamCount !in virtualParamCounts) return
        
        // 解析这个调用实际调用的是哪个方法
        val resolvedMethod = callExpression.resolveMethod() ?: return
        
        // 判断是否是虚拟方法（LightMethodBuilder）且参数数量匹配
        if (resolvedMethod !is LightMethodBuilder) return
        if (resolvedMethod.parameterList.parametersCount != actualParamCount) return
        
        // 判断虚拟方法的导航目标是否是原方法
        if (resolvedMethod.navigationElement != originalMethod) return
        
        // 找到了！将这个引用添加到结果中
        methodExpression.reference?.let { consumer.process(it) }
    }
}

