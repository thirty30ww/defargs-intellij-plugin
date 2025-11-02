package io.github.thirty30ww.defargs.intellij.search

import com.intellij.openapi.application.QueryExecutorBase
import com.intellij.psi.*
import com.intellij.psi.search.searches.DefinitionsScopedSearch
import com.intellij.util.Processor
import io.github.thirty30ww.defargs.intellij.util.AnnotationAnalyzer
import io.github.thirty30ww.defargs.intellij.util.MethodAnalyzer

/**
 * 虚拟方法实现搜索器
 * 
 * 作用：让 IntelliJ 在查找接口方法实现时，能够找到实现类中的虚拟重载方法
 * 
 * 工作原理：
 * 1. 当用户在接口方法上执行"查找实现"时，此搜索器会被调用
 * 2. 检查接口方法是否有 @Omittable 注解的参数
 * 3. 如果有，查找该接口方法的所有实现类
 * 4. 在实现类中查找生成的虚拟重载方法
 * 5. 将这些虚拟方法添加到搜索结果中
 * 
 * 示例：
 * ```
 * // 接口定义
 * public interface Service {
 *     void test(String a, @Omittable String b);
 * }
 * 
 * // 实现类
 * public class ServiceImpl implements Service {
 *     @Override
 *     public void test(String a, @DefaultValue("b") String b) { ... }
 *     // 编译时生成：public void test(String a) { test(a, "b"); }
 * }
 * 
 * // 当在接口的 test 方法上"查找实现"时，应该能找到：
 * // 1. ServiceImpl.test(String, String) - 完整实现 ✓（默认能找到）
 * // 2. ServiceImpl.test(String) - 虚拟重载方法 ✓（由此搜索器提供）
 * ```
 */
class ImplementationSearcher : QueryExecutorBase<PsiElement, DefinitionsScopedSearch.SearchParameters>(true) {

    /**
     * 处理查询，查找虚拟重载方法的实现
     * @param queryParameters 搜索参数，包含要查找实现的元素
     * @param consumer 结果处理器，用于将找到的实现添加到搜索结果中
     */
    override fun processQuery(
        queryParameters: DefinitionsScopedSearch.SearchParameters,
        consumer: Processor<in PsiElement>
    ) {
        val element = queryParameters.element
        
        // 只处理方法
        if (element !is PsiMethod) return
        
        // 只处理接口或抽象类中的抽象方法
        val containingClass = element.containingClass ?: return
        if (!containingClass.isInterface && !containingClass.hasModifierProperty(PsiModifier.ABSTRACT)) return
        if (!element.hasModifierProperty(PsiModifier.ABSTRACT)) return
        
        // 分析方法，获取虚拟重载方法的参数数量列表
        val virtualOverloadParamCounts = AnnotationAnalyzer.analyzeMethod(element)
        
        // 如果没有可省略参数，无需处理
        if (virtualOverloadParamCounts.isEmpty()) return
        
        // 查找接口方法的实现
        searchVirtualImplementations(element, virtualOverloadParamCounts, consumer)
    }
    
    /**
     * 搜索虚拟重载方法的实现
     * 
     * @param interfaceMethod 接口中的原始方法
     * @param virtualParamCounts 虚拟重载方法的参数数量列表
     * @param consumer 结果处理器
     */
    private fun searchVirtualImplementations(
        interfaceMethod: PsiMethod,
        virtualParamCounts: List<Int>,
        consumer: Processor<in PsiElement>
    ) {
        // 使用 MethodAnalyzer 查找所有实现该接口方法的类方法
        val implementations = MethodAnalyzer.findImplementations(interfaceMethod)
        
        // 对于每个实现类方法，查找其虚拟重载方法
        for (implMethod in implementations) {
            val implClass = implMethod.containingClass ?: continue
            
            // 在实现类中查找虚拟重载方法
            for (paramCount in virtualParamCounts) {
                val virtualMethod = MethodAnalyzer.findVirtualMethod(
                    implClass, 
                    interfaceMethod.name, 
                    paramCount, 
                    implMethod
                )
                if (virtualMethod != null) {
                    consumer.process(virtualMethod)
                }
            }
        }
    }
}

