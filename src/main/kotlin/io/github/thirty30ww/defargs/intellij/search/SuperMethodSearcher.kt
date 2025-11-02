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
 * 虚拟方法父方法搜索器
 * 
 * 作用：让 IntelliJ 在查找实现类虚拟方法的父方法时，能够找到接口中的虚拟方法
 * 
 * 工作原理：
 * 1. 当用户在实现类的虚拟方法上执行"查找父方法"时，此搜索器会被调用
 * 2. 检查这个虚拟方法对应的原始方法
 * 3. 查找原始方法的所有父方法（接口/抽象类中的方法）
 * 4. 在这些父方法中查找对应参数数量的虚拟方法
 * 5. 将这些虚拟父方法添加到搜索结果中
 * 
 * 示例：
 * ```
 * // 接口定义
 * public interface Service {
 *     void test(String a, @Omittable String b);
 *     // 编译时生成：void test(String a);  // 虚拟方法A
 * }
 * 
 * // 实现类
 * public class ServiceImpl implements Service {
 *     @Override
 *     public void test(String a, @DefaultValue("b") String b) { ... }
 *     // 编译时生成：public void test(String a) { test(a, "b"); }  // 虚拟方法B
 * }
 * 
 * // 当在 ServiceImpl.test(String) 上"查找父方法"时，应该能找到：
 * // 1. Service.test(String) - 接口的虚拟方法A ✓（由此搜索器提供）
 * ```
 */
class SuperMethodSearcher : QueryExecutorBase<MethodSignature, SuperMethodsSearch.SearchParameters>(true) {

    /**
     * 处理查询，查找虚拟方法的父方法
     * @param queryParameters 搜索参数，包含要查找父方法的方法
     * @param consumer 结果处理器，用于将找到的父方法添加到搜索结果中
     */
    override fun processQuery(
        queryParameters: SuperMethodsSearch.SearchParameters,
        consumer: Processor<in MethodSignature>
    ) {
        // 防止递归调用
        if (isProcessing.get()) {
            return
        }
        
        val method = queryParameters.method
        
        // 情况1: 在虚拟方法上查找父方法
        if (method is LightMethodBuilder) {
            handleVirtualMethod(method, consumer)
            return
        }
        
        // 情况2: 在原始方法上查找父方法，需要找到接口的虚拟方法
        handleOriginalMethod(method, consumer)
    }
    
    /**
     * 处理虚拟方法的父方法查找
     * 
     * @param virtualMethod 虚拟方法（LightMethodBuilder）
     * @param consumer 结果处理器
     */
    private fun handleVirtualMethod(virtualMethod: LightMethodBuilder, consumer: Processor<in MethodSignature>) {
        // 获取虚拟方法的导航目标（原始实现方法）
        val originalMethod = virtualMethod.navigationElement as? PsiMethod ?: return
        
        // 只处理有注解的方法
        val virtualParamCounts = AnnotationAnalyzer.analyzeMethod(originalMethod)
        if (virtualParamCounts.isEmpty()) return
        
        // 检查当前虚拟方法的参数数量是否在生成的重载列表中
        val methodParamCount = virtualMethod.parameterList.parametersCount
        if (methodParamCount !in virtualParamCounts) return
        
        // 查找原始方法的父方法，然后查找对应的虚拟父方法
        searchVirtualSuperMethods(originalMethod, methodParamCount, consumer)
    }
    
    /**
     * 处理原始方法的父方法查找
     * 
     * @param method 原始方法
     * @param consumer 结果处理器
     */
    private fun handleOriginalMethod(method: PsiMethod, consumer: Processor<in MethodSignature>) {
        // 只处理有注解的方法
        val virtualParamCounts = AnnotationAnalyzer.analyzeMethod(method)
        if (virtualParamCounts.isEmpty()) return
        
        // 对于原始方法，查找所有对应参数数量的虚拟父方法
        for (paramCount in virtualParamCounts) {
            searchVirtualSuperMethods(method, paramCount, consumer)
        }
    }
    
    /**
     * 搜索虚拟方法的父方法（不使用递归搜索）
     * 
     * @param implMethod 实现类中的原始方法
     * @param virtualParamCount 虚拟方法的参数数量
     * @param consumer 结果处理器
     */
    private fun searchVirtualSuperMethods(
        implMethod: PsiMethod,
        virtualParamCount: Int,
        consumer: Processor<in MethodSignature>
    ) {
        // 设置标志防止递归
        isProcessing.set(true)
        try {
            // 查找原始方法的所有父方法（接口/抽象类中的方法）
            // 这里会触发 SuperMethodsSearch，但因为我们设置了标志，不会递归
            val superMethods = implMethod.findSuperMethods()
            
            // 对于每个父方法，查找对应的手动重载方法
            for (superMethod in superMethods) {
                val superClass = superMethod.containingClass ?: continue
                
                // 在父类/接口中查找相同方法名、相同参数数量的手动重载方法
                val allMethods = superClass.findMethodsByName(implMethod.name, false)
                
                val manualOverload = allMethods.find { 
                    it.parameterList.parametersCount == virtualParamCount && 
                    !it.hasModifierProperty(PsiModifier.STATIC) && // 排除静态方法
                    areMethodSignaturesCompatible(implMethod, it, virtualParamCount)
                }
                
                if (manualOverload != null) {
                    // 将 PsiMethod 转换为 MethodSignature
                    val signature = MethodSignatureBackedByPsiMethod.create(
                        manualOverload, 
                        PsiSubstitutor.EMPTY
                    )
                    consumer.process(signature)
                }
            }
        } finally {
            // 恢复标志
            isProcessing.set(false)
        }
    }
    
    /**
     * 检查两个方法的签名是否兼容（用于判断是否是覆盖关系）
     * 
     * @param implMethod 实现类的方法
     * @param superMethod 父类/接口的方法
     * @param virtualParamCount 虚拟方法的参数数量
     * @return true 如果签名兼容
     */
    private fun areMethodSignaturesCompatible(
        implMethod: PsiMethod,
        superMethod: PsiMethod,
        virtualParamCount: Int
    ): Boolean {
        // 方法名必须相同
        if (implMethod.name != superMethod.name) return false
        
        // 参数数量必须匹配
        if (superMethod.parameterList.parametersCount != virtualParamCount) return false
        
        // 检查前 N 个参数类型是否匹配
        val implParams = implMethod.parameterList.parameters
        val superParams = superMethod.parameterList.parameters
        
        for (i in 0 until virtualParamCount) {
            val implType = implParams[i].type
            val superType = superParams[i].type
            
            // 类型必须相同或兼容
            if (!implType.isAssignableFrom(superType) && !superType.isAssignableFrom(implType)) {
                return false
            }
        }
        
        return true
    }
}

