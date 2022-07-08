package com.hluhovskyi.zero.common

import kotlinx.coroutines.CoroutineScope
import java.io.Closeable

interface CloseableCoroutineScope : CoroutineScope, Closeable