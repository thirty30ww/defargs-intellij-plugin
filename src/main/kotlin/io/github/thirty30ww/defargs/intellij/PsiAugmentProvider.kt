package io.github.thirty30ww.defargs.intellij

import com.intellij.psi.*
import com.intellij.psi.augment.PsiAugmentProvider as IntelliJPsiAugmentProvider
import com.intellij.psi.impl.light.LightMethodBuilder
import com.intellij.psi.impl.source.PsiExtensibleClass
import io.github.thirty30ww.defargs.intellij.util.AnnotationAnalyzer

/**
 * PSI 增强提供者，为带有 @DefaultValue 或 @Omittable 注解的方法生成虚拟的重载方法
 * 这样 IDEA 就能识别这些由注解处理器在编译时生成的方法
 */
class PsiAugmentProvider : IntelliJPsiAugmentProvider() {

    /**
     * 为指定的 PsiElement 提供增强的 PsiElement 列表
     * 仅当 element 是 PsiExtensibleClass 类型时才会被调用
     * 用于为类中的方法添加虚拟的重载方法
     */
    @Deprecated("Deprecated in Java")
    override fun <Psi : PsiElement> getAugments(
        element: PsiElement,
        type: Class<Psi>
    ): MutableList<Psi> {
        // 只处理方法类型的增强
        if (type != PsiMethod::class.java) {
            return mutableListOf()
        }
        
        // 只处理类元素
        if (element !is PsiExtensibleClass) {
            return mutableListOf()
        }
        
        val result = mutableListOf<PsiMethod>()
        
        try {
            generateOverloadMethods(element, result)
        } catch (e: Exception) {
            // 记录错误但不中断处理
            println("Error in DefArgsPsiAugmentProvider: ${e.message}")
        }
        
        @Suppress("UNCHECKED_CAST")
        return result as MutableList<Psi>
    }
    
    /**
     * 为类中的所有方法生成虚拟重载方法
     */
    private fun generateOverloadMethods(containingClass: PsiExtensibleClass, result: MutableList<PsiMethod>) {
        for (method in containingClass.ownMethods) {
            // 跳过构造函数
            if (method.isConstructor) {
                continue
            }
            
            generateOverloadsForMethod(method, containingClass, result)
        }
    }
    
    /**
     * 为单个方法生成虚拟重载方法
     */
    private fun generateOverloadsForMethod(
        method: PsiMethod,
        containingClass: PsiExtensibleClass,
        result: MutableList<PsiMethod>
    ) {
        // 分析方法，查看是否需要生成重载
        val overloadParameterCounts = AnnotationAnalyzer.analyzeMethod(method)
        
        // 为每个需要的重载生成虚拟方法
        for (paramCount in overloadParameterCounts) {
            // 检查是否已存在相同签名的方法
            if (AnnotationAnalyzer.methodExists(containingClass, method.name, paramCount)) {
                continue
            }
            
            // 创建虚拟的重载方法
            val lightMethod = createLightMethod(method, paramCount, containingClass)
            result.add(lightMethod)
        }
    }
    
    /**
     * 创建一个轻量级的虚拟方法
     */
    private fun createLightMethod(
        originalMethod: PsiMethod,
        parameterCount: Int,
        containingClass: PsiClass
    ): LightMethodBuilder {
        val builder = LightMethodBuilder(originalMethod.manager, originalMethod.name)
        
        builder.containingClass = containingClass
        originalMethod.returnType?.let { builder.setMethodReturnType(it) }
        
        copyModifiers(originalMethod, builder)
        copyParameters(originalMethod, builder, parameterCount)
        copyTypeParameters(originalMethod, builder)
        copyExceptions(originalMethod, builder)
        
        builder.navigationElement = originalMethod
        
        return builder
    }
    
    /**
     * 复制方法的修饰符
     */
    private fun copyModifiers(originalMethod: PsiMethod, builder: LightMethodBuilder) {
        val modifierList = originalMethod.modifierList
        
        // 复制访问修饰符
        if (modifierList.hasModifierProperty(PsiModifier.PUBLIC)) {
            builder.addModifier(PsiModifier.PUBLIC)
        }
        if (modifierList.hasModifierProperty(PsiModifier.PROTECTED)) {
            builder.addModifier(PsiModifier.PROTECTED)
        }
        if (modifierList.hasModifierProperty(PsiModifier.PRIVATE)) {
            builder.addModifier(PsiModifier.PRIVATE)
        }
        
        // 复制其他修饰符
        if (modifierList.hasModifierProperty(PsiModifier.STATIC)) {
            builder.addModifier(PsiModifier.STATIC)
        }
        if (modifierList.hasModifierProperty(PsiModifier.FINAL)) {
            builder.addModifier(PsiModifier.FINAL)
        }
        if (modifierList.hasModifierProperty(PsiModifier.SYNCHRONIZED)) {
            builder.addModifier(PsiModifier.SYNCHRONIZED)
        }
        if (modifierList.hasModifierProperty(PsiModifier.ABSTRACT)) {
            builder.addModifier(PsiModifier.ABSTRACT)
        }
    }
    
    /**
     * 复制方法的参数（只复制前 parameterCount 个）
     */
    private fun copyParameters(originalMethod: PsiMethod, builder: LightMethodBuilder, parameterCount: Int) {
        val originalParams = originalMethod.parameterList.parameters
        for (i in 0 until parameterCount.coerceAtMost(originalParams.size)) {
            val param = originalParams[i]
            builder.addParameter(param.name ?: "p$i", param.type)
        }
    }
    
    /**
     * 复制方法的类型参数（泛型）
     */
    private fun copyTypeParameters(originalMethod: PsiMethod, builder: LightMethodBuilder) {
        originalMethod.typeParameters.forEach { typeParam ->
            builder.addTypeParameter(typeParam)
        }
    }
    
    /**
     * 复制方法抛出的异常
     */
    private fun copyExceptions(originalMethod: PsiMethod, builder: LightMethodBuilder) {
        originalMethod.throwsList.referencedTypes.forEach { exceptionType ->
            builder.addException(exceptionType)
        }
    }
}

