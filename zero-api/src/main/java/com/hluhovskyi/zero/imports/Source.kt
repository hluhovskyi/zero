package com.hluhovskyi.zero.imports

interface Source {
    val key: String

    /** Whether selecting this source requires the user to pick a local file. Drive-backed
     *  sources fetch remotely and override this to `false`, skipping the file picker. */
    val requiresFile: Boolean get() = true
}
