package io.skjaere.mocknntp.testcontainer

import io.skjaere.compressionutils.generation.ArchiveGenerator
import io.skjaere.compressionutils.generation.ArchiveVolume
import io.skjaere.compressionutils.generation.ContainerType

object TestArchiveHelper {
    fun createArchive(
        files: Map<String, ByteArray>,
        containerType: ContainerType,
        numberOfVolumes: Int = 1
    ): List<ArchiveVolume> {
        return files.flatMap { (filename, data) ->
            ArchiveGenerator.generate(data, numberOfVolumes, containerType, filename)
        }
    }
}
