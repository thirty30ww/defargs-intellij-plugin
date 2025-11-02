package io.github.thirty30ww.defargs.intellij.inspection

import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.*
import io.github.thirty30ww.defargs.intellij.constant.DefArgsConstants
import io.github.thirty30ww.defargs.intellij.quickfix.AddOmittableToSuperMethodQuickFix
import io.github.thirty30ww.defargs.intellij.util.AnnotationAnalyzer
import io.github.thirty30ww.defargs.intellij.util.MessageBuilder
import io.github.thirty30ww.defargs.intellij.util.MethodAnalyzer

/**
 * 检查接口方法和实现方法的注解是否匹配
 * 
 * 规则：
 * - 实现方法的 @DefaultValue 必须对应父方法的 @Omittable
 * - 父方法的 @Omittable 不强制要求实现方法有 @DefaultValue（可以手动实现重载）
 */
class AnnotationMismatchInspection : AbstractBaseJavaLocalInspectionTool() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return object : JavaElementVisitor() {
            override fun visitMethod(method: PsiMethod) {
                super.visitMethod(method)
                checkOverrideMethod(method, holder)
            }
        }
    }
    
    /**
     * 检查重写/实现方法的注解匹配
     *
     * @param method 要检查的方法
     * @param holder 问题报告器
     */
    private fun checkOverrideMethod(method: PsiMethod, holder: ProblemsHolder) {
        // 只检查有 @Override 的方法
        if (!method.hasAnnotation("java.lang.Override")) {
            return
        }
        
        // 获取被重写/实现的方法
        val superMethods = method.findSuperMethods()
        if (superMethods.isEmpty()) {
            return
        }
        
        // 检查每个父方法
        for (superMethod in superMethods) {
            checkParameterAnnotations(method, superMethod, holder)
        }
    }
    
    /**
     * 检查实现方法和父方法的参数注解是否匹配
     *
     * @param implMethod 实现方法
     * @param superMethod 父方法
     * @param holder 问题报告器
     */
    private fun checkParameterAnnotations(
        implMethod: PsiMethod,
        superMethod: PsiMethod,
        holder: ProblemsHolder
    ) {
        val implParams = implMethod.parameterList.parameters
        val superParams = superMethod.parameterList.parameters
        
        // 参数数量必须相同
        if (implParams.size != superParams.size) {
            return
        }
        
        // 检查每个参数
        for (i in implParams.indices) {
            checkParameter(implMethod, implParams[i], superParams[i], superMethod, i, holder)
        }
    }
    
    /**
     * 检查单个参数的注解匹配
     *
     * @param implMethod 实现方法
     * @param implParam 实现方法的参数
     * @param superParam 父方法的参数
     * @param superMethod 父方法
     * @param paramIndex 参数索引
     * @param holder 问题报告器
     */
    private fun checkParameter(
        implMethod: PsiMethod,
        implParam: PsiParameter,
        superParam: PsiParameter,
        superMethod: PsiMethod,
        paramIndex: Int,
        holder: ProblemsHolder
    ) {
        val implHasDefaultValue = AnnotationAnalyzer.hasDefaultValueAnnotation(implParam)
        val superHasOmittable = AnnotationAnalyzer.hasOmittableAnnotation(superParam)
        
        // 规则：实现方法有 @DefaultValue，父方法必须有 @Omittable 或存在对应的重载方法
        if (implHasDefaultValue && !superHasOmittable) {
            checkOverloadMethodExists(implMethod, implParam, superMethod, paramIndex, holder)
        }
        
        // 注意：父方法有 @Omittable，实现方法不一定要有 @DefaultValue
        // 因为实现类可以选择手动实现所有重载方法，所以不检查这种情况
    }
    
    /**
     * 检查父接口/类中是否存在对应的重载方法
     *
     * @param implMethod 实现方法
     * @param implParam 实现方法的参数
     * @param superMethod 父方法
     * @param paramIndex 参数索引
     * @param holder 问题报告器
     */
    private fun checkOverloadMethodExists(
        implMethod: PsiMethod,
        implParam: PsiParameter,
        superMethod: PsiMethod,
        paramIndex: Int,
        holder: ProblemsHolder
    ) {
        val superClass = superMethod.containingClass ?: return
        
        // 计算需要生成的重载方法的参数数量
        val overloadParamCounts = AnnotationAnalyzer.analyzeMethod(implMethod)
        
        // 检查每个重载方法是否存在于父接口/类中
        for (paramCount in overloadParamCounts) {
            if (!methodExistsInSuper(superClass, superMethod.name, paramCount)) {
                // 找到 @Override 注解
                val overrideAnnotation = implMethod.modifierList.findAnnotation("java.lang.Override")
                    ?: return // 没有 @Override 注解，不报告

                val superClassName = superClass.qualifiedName ?: superClass.name ?: "Unknown"
                val parameterList = MethodAnalyzer.buildParameterList(superMethod, paramCount)
                
                holder.registerProblem(
                    overrideAnnotation,
                    MessageBuilder.missingOverloadInSuperType(
                        superClassName,
                        superMethod.name,
                        parameterList,
                        implParam.name ?: "parameter"
                    ),
                    AddOmittableToSuperMethodQuickFix(superMethod, paramIndex)
                )
                return // 只报告第一个缺失的重载
            }
        }
    }
    
    /**
     * 检查父接口/类中是否存在指定签名的方法
     */
    private fun methodExistsInSuper(
        superClass: PsiClass,
        methodName: String,
        paramCount: Int
    ): Boolean {
        // 查找所有同名方法
        val methods = superClass.findMethodsByName(methodName, false)
        
        // 检查是否有匹配的参数数量
        return methods.any { it.parameterList.parametersCount == paramCount }
    }
}

