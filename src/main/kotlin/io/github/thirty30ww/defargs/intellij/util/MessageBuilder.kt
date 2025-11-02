package io.github.thirty30ww.defargs.intellij.util

/**
 * 消息构建器
 * 统一管理所有错误和提示信息
 */
object MessageBuilder {
    
    /**
     * 方法定义冲突错误
     */
    fun methodAlreadyDefined(className: String, methodName: String, paramTypes: String): String {
        return "【DefaultValue】已在类 $className 中定义了方法 $methodName($paramTypes)"
    }
    
    /**
     * 方法与 @DefaultValue 冲突
     */
    fun methodConflictsWithDefaultValue(): String {
        return "【DefaultValue】此方法与 @DefaultValue 生成的重载方法冲突"
    }
    
    /**
     * 方法调用不明确
     */
    fun methodCallAmbiguous(className: String, methodName: String, paramTypes: String): String {
        return "【DefaultValue】方法调用不明确。$className 中的<br>$methodName($paramTypes) 和 $className 中的<br>$methodName($paramTypes) 均匹配"
    }
    
    /**
     * 参数不能同时使用两个注解
     */
    fun annotationsMutuallyExclusive(paramName: String): String {
        return "参数 '$paramName' 不能同时使用 @DefaultValue 和 @Omittable 注解"
    }
    
    /**
     * @DefaultValue 不能用于抽象方法
     */
    fun defaultValueOnAbstractMethod(): String {
        return "@DefaultValue 不能用于抽象方法。抽象方法请使用 @Omittable 注解"
    }
    
    /**
     * @Omittable 只能用于抽象方法
     */
    fun omittableOnConcreteMethod(): String {
        return "@Omittable 只能用于抽象方法。具体方法请使用 @DefaultValue 注解"
    }
    
    /**
     * 父类型缺少对应的重载方法
     */
    fun missingOverloadInSuperType(
        superTypeName: String,
        methodName: String,
        parameterList: String,
        missingParamName: String
    ): String {
        return "父类型 '$superTypeName' 中缺少对应的重载方法 $methodName($parameterList)。" +
               "参数 '$missingParamName' 有 @DefaultValue，请在父类型中添加 @Omittable 注解或手动定义重载方法"
    }
}

