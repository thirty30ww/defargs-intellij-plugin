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
}

