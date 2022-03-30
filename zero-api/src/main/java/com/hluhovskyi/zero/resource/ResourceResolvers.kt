package com.hluhovskyi.zero.resource

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlin.reflect.KClass

inline fun <reified Request : ResourceRequest<Result>, Result> resourceResolverOf(
    noinline resolver: (request: Request) -> Flow<ResourceStatus<Result>>
): ResourceResolver = TypedResourceResolver<Request, Result>(
    requestClass = Request::class,
    resolver = resolver
)

@PublishedApi
internal class TypedResourceResolver<R : ResourceRequest<T>, T>(
    private val requestClass: KClass<R>,
    private val resolver: (request: R) -> Flow<ResourceStatus<T>>
) : ResourceResolver {

    @Suppress("unchecked_cast")
    override fun <Request : ResourceRequest<Result>, Result> resolve(request: Request): Flow<ResourceStatus<Result>> =
        if (requestClass.isInstance(request)) {
            resolver(request as R) as Flow<ResourceStatus<Result>>
        } else {
            emptyFlow()
        }
}