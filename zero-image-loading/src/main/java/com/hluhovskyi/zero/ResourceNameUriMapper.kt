package com.hluhovskyi.zero

import coil3.Uri
import coil3.map.Mapper
import coil3.pathSegments
import coil3.request.Options
import coil3.toUri

/**
 * Coil 3's [coil3.fetch.ResourceUriFetcher] only accepts numeric resource URIs
 * (`android.resource://<pkg>/<resId>`). Coil 2 also resolved the `<pkg>/<type>/<name>` form the
 * app builds via `AndroidUriResourceFactory`; Coil 3 dropped it (named resource URIs block
 * resource shrinking). Registering this mapper restores that resolution inside the ImageLoader
 * pipeline, so callers keep passing opaque [com.hluhovskyi.zero.common.Uri]s and the loader stays
 * agnostic to whether an image is a resource, asset, file, or network URL.
 */
internal class ResourceNameUriMapper : Mapper<String, Uri> {

    override fun map(data: String, options: Options): Uri? {
        val uri = data.toUri()
        if (uri.scheme != "android.resource") return null
        val pkg = uri.authority?.takeIf(String::isNotBlank) ?: return null
        // Only the by-name form needs rewriting; the numeric form is already a single segment.
        val segments = uri.pathSegments
        if (segments.size != 2) return null
        val (type, name) = segments
        if (name.toIntOrNull() != null) return null
        val resId = options.context.resources.getIdentifier(name, type, pkg)
        return if (resId != 0) "android.resource://$pkg/$resId".toUri() else null
    }
}
