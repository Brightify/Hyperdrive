import org.gradle.api.provider.Property

abstract class PublishingMetadata {
    abstract val name: Property<String>

    abstract val description: Property<String>
}
