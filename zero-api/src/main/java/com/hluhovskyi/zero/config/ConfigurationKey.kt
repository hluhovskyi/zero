package com.hluhovskyi.zero.config

interface ConfigurationKey<Value> {
    val name: String
    val defaultValue: Value
}
