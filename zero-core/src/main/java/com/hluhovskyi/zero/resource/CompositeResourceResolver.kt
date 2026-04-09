package com.hluhovskyi.zero.resource

import kotlinx.coroutines.flow.Flow
import kotlin.reflect.KClass

internal class CompositeResourceResolver(
    private val resolvers: Map<KClass<out ResourceRequest<*>>, ResourceResolver>,
) : ResourceResolver {

    @Suppress("unchecked_cast")
    override fun <Request : ResourceRequest<Result>, Result> resolve(request: Request): Flow<ResourceStatus<Result>> {
        val resolver = resolvers[request::class as KClass<ResourceRequest<*>>] ?: ResourceResolver.Noop
        return resolver.resolve(request)
    }
}
