package io.github.thirty30ww.defargs.intellij.search

import com.intellij.openapi.application.QueryExecutorBase
import com.intellij.psi.*
import com.intellij.psi.impl.light.LightMethodBuilder
import com.intellij.psi.search.searches.SuperMethodsSearch
import com.intellij.psi.util.MethodSignature
import com.intellij.psi.util.MethodSignatureBackedByPsiMethod
import com.intellij.util.Processor
import io.github.thirty30ww.defargs.intellij.util.AnnotationAnalyzer
import io.github.thirty30ww.defargs.intellij.util.MethodAnalyzer

/**
 * 线程本地标志，用于防止递归调用
 */
private val isProcessing = ThreadLocal.withInitial { false }

/**
 * 手动重载方法父方法搜索器
 * 
 * 作用：让 IntelliJ 在查找实现类原始方法的父方法时，能够找到接口中手动定义的重载方法
 * 
 * 工作原理：
 * 1. 当用户在实现类的原始方法上执行"查找父方法"时，此搜索器会被调用
 * 2. 检查原始方法是否有 @DefaultValue 或 @Omittable 注解（会生成虚拟方法）
 * 3. 查找原始方法的所有父方法（接口/抽象类中的方法）
 * 4. 在父接口/抽象类中查找手动定义的重载方法（参数数量与虚拟方法相同）
 * 5. 将这些手动重载方法添加到搜索结果中
 * 
 * 示例：
 * ```
 * // 接口定义（手动写了两个重载）
 * public interface TestService {
 *     int test(int a, int b);
 *     int test(int a);  // 手动重载
 * }
 * 
 * // 实现类
 * public class TestServiceImpl implements TestService {
 *     @Override
 *     public int test(int a, @DefaultValue("2") int b) { ... }
 *     // 编译时生成虚拟方法：public int test(int a) { return test(a, 2); }
 * }
 * 
 * // 当在 TestServiceImpl.test(int, int) 上"查找父方法"时，应该能找到：
 * // 1. TestService.test(int, int) ✓（IntelliJ 默认行为）
 * // 2. TestService.test(int) ✓（由此搜索器提供）
 * ```
 */
class SuperMethodSearcher : QueryExecutorBase<MethodSignature, SuperMethodsSearch.SearchParameters>(true) {

    override fun processQuery(
        queryParameters: SuperMethodsSearch.SearchParameters,
        consumer: Processor<in MethodSignature>
    ) {
        // 防止递归
        if (isProcessing.get()) return
        
        val method = queryParameters.method
        
        // 只处理原始方法，不处理虚拟方法
        if (method is LightMethodBuilder) return
        
        // 获取可省略参数对应的虚拟方法参数数量列表
        val paramCounts = AnnotationAnalyzer.analyzeMethod(method)
        if (paramCounts.isEmpty()) return
        
        // 查找接口中的手动重载方法
        findManualOverloads(method, paramCounts, consumer)
    }
    
    /**
     * 查找接口中的手动重载方法
     *
     * @param method 实现类中的原始方法
     * @param paramCounts 可省略参数对应的虚拟方法参数数量列表
     * @param consumer 结果处理器
     */
    private fun findManualOverloads(
        method: PsiMethod,
        paramCounts: List<Int>,
        consumer: Processor<in MethodSignature>
    ) {
        isProcessing.set(true)
        try {
            // 查找所有父接口/抽象类中的方法
            val superMethods = method.findSuperMethods()

            for (superMethod in superMethods) {
                // 跳过非接口/抽象类的父方法，如继承的普通方法
                val superClass = superMethod.containingClass ?: continue

                // 查找手动重载方法
                for (paramCount in paramCounts) {
                    val overload = MethodAnalyzer.findOverloadMethod(
                        superClass,
                        method.name,
                        paramCount,
                        method
                    )

                    // 如果找到手动重载方法，添加到结果中
                    if (overload != null) {
                        val signature = MethodSignatureBackedByPsiMethod.create(
                            overload,
                            PsiSubstitutor.EMPTY
                        )
                        consumer.process(signature)
                    }
                }
            }
        } finally {
            isProcessing.set(false)
        }
    }
}

