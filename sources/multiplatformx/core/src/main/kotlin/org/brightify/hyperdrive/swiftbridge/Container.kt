package org.brightify.hyperdrive.swiftbridge

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import java.io.File
import java.io.OutputStream
import java.net.URI
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.zip.ZipFile

public object KLibraryMetadataReaderWriter {
    fun read(file: File): HyperdriveMetadata? {
        require(file.extension == "klib")
        require(file.exists()) { "File $file does not exist."}

        val zipFile = ZipFile(file)
        val metadataEntry = zipFile.getEntry("resources/hyperdrive.metadata") ?: return null
        val bytes = zipFile.getInputStream(metadataEntry).readBytes()

        return ProtoBuf.decodeFromByteArray(bytes)
    }

    fun write(metadata: HyperdriveMetadata, outputStream: OutputStream) {
        outputStream.write(ProtoBuf.encodeToByteArray(metadata))
    }

    fun copy(metadataFile: File, libraryFile: File) {
        require(metadataFile.exists())
        require(libraryFile.extension == "klib")
        require(libraryFile.exists())

        val uri = URI.create("jar:${libraryFile.toURI()}")
        val env = emptyMap<String, Any?>()
        FileSystems.getFileSystem(uri).use { zipfs ->
            Files.copy(metadataFile.toPath(), zipfs.getPath("/resources/hyperdrive.metadata"), StandardCopyOption.REPLACE_EXISTING)
        }
    }
}

@Serializable
data class HyperdriveMetadata(
    val containers: List<Container>
)

@Serializable
data class Container(
    val name: String,
    val properties: List<Property>,
    val functions: List<Function>,
)

@Serializable
data class Property(
    val name: String,
    val type: Type,
)

@Serializable
data class Function(
    val isSuspend: Boolean,
    val parameters: List<Parameter>,
    val returnType: Type,
)

@Serializable
data class Parameter(
    val name: String,
    val type: Type,
)

@Serializable
data class Type(
    val name: String
)