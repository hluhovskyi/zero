package com.hluhovskyi.zero.config

interface Scope {
    val scope: String
}

interface ScopedConfigurationKey<Value> :
    ConfigurationKey<Value>,
    Scope

fun scopeOf(name: String): Scope = ValueScope(name)

@JvmInline
private value class ValueScope(override val scope: String) : Scope
