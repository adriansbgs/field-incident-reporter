package com.incidentreporter.field.data.photo

import java.io.File

/** Owns the photo files. The database stores only file names; they live under [dir]. */
class PhotoStore(val dir: File) {

    fun delete(fileNames: Collection<String>) {
        fileNames.forEach { File(dir, it).delete() }
    }
}
