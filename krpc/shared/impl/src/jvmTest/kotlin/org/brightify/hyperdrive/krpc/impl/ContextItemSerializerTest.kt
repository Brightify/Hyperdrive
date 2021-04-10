package org.brightify.hyperdrive.krpc.impl

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import org.brightify.hyperdrive.krpc.session.Session

@Serializable
data class SomeValue(
    val hello: String,
    val world: Int,
)

object RegisteredKey: Session.Context.Key<SomeValue> {
    override val qualifiedName: String = "RegisteredKey"
    override val serializer: KSerializer<SomeValue> = SomeValue.serializer()
}

object UnregisteredKey: Session.Context.Key<SomeValue> {
    override val qualifiedName: String = "UnregisteredKey"
    override val serializer: KSerializer<SomeValue> = SomeValue.serializer()
}
//
// class ContextItemSerializerTest: BehaviorSpec({
//
//     Given("ContextItemSerializer") {
//         val serializer = ContextItemSerializer(object: ContextKeyRegistry {
//             override fun getKeyByQualifiedName(keyQualifiedName: String): Session.Context.Key<*>? {
//                 return if (keyQualifiedName == RegisteredKey.qualifiedName) {
//                     RegisteredKey
//                 } else {
//                     null
//                 }
//             }
//         })
//
//         val someValue = SomeValue("Hello", 5)
//         And("ProtoBuf coder") {
//             val coder = ProtoBuf {  }
//
//             When("Serializing a supported item") {
//                 val item = Session.Context.Item(RegisteredKey, 0, someValue)
//                 val bytes = coder.encodeToByteArray(serializer, item)
//                 Then("Is deserialized") {
//                     val deserializedItem = coder.decodeFromByteArray(serializer, bytes)
//                     deserializedItem.key shouldBe RegisteredKey
//                     deserializedItem.revision shouldBe item.revision
//                     deserializedItem.value shouldBe someValue
//                 }
//             }
//
//             When("Serializing an item unsupported by deserializer") {
//                 val item = Session.Context.Item(UnregisteredKey, 0, someValue)
//                 val bytes = coder.encodeToByteArray(serializer, item)
//
//                 Then("Is deserialized as UnsupportedKey with ByteArray type") {
//                     val deserializedItem = coder.decodeFromByteArray(serializer, bytes)
//                     deserializedItem.key should beInstanceOf<UnsupportedKey>()
//                     deserializedItem.key.qualifiedName shouldBe UnregisteredKey.qualifiedName
//                     deserializedItem.revision shouldBe 0
//                     deserializedItem.value should beInstanceOf<ByteArray>()
//                 }
//             }
//
//             When("Serializing an item unsupported by serializer") {
//                 val setupItem = Session.Context.Item(UnregisteredKey, 0, someValue)
//                 val setupBytes = coder.encodeToByteArray(serializer, setupItem)
//                 val setupDeserializedItem = coder.decodeFromByteArray(serializer, setupBytes)
//                 val item = Session.Context.Item(UnsupportedKey(RegisteredKey.qualifiedName), 0, setupDeserializedItem.value as ByteArray)
//                 val bytes = coder.encodeToByteArray(serializer, item)
//
//                 Then("Is deserialized as RegisteredKey with correct type") {
//                     val deserializedItem = coder.decodeFromByteArray(serializer, bytes)
//                     deserializedItem.key shouldBe RegisteredKey
//                     deserializedItem.revision shouldBe 0
//                     deserializedItem.value shouldBe someValue
//                 }
//             }
//         }
//
//         And("Json coder") {
//             val coder = Json {  }
//
//             When("Serializing a supported item") {
//                 val item = Session.Context.Item(RegisteredKey, 0, someValue)
//                 val string = coder.encodeToString(serializer, item)
//                 Then("Is deserialized") {
//                     val deserializedItem = coder.decodeFromString(serializer, string)
//                     deserializedItem.key shouldBe RegisteredKey
//                     deserializedItem.revision shouldBe item.revision
//                     deserializedItem.value shouldBe someValue
//                 }
//             }
//
//             When("Serializing an item unsupported by deserializer") {
//                 val item = Session.Context.Item(UnregisteredKey, 0, someValue)
//                 val string = coder.encodeToString(serializer, item)
//
//                 Then("Is deserialized as UnsupportedKey with ByteArray type") {
//                     val deserializedItem = coder.decodeFromString(serializer, string)
//                     deserializedItem.key should beInstanceOf<UnsupportedKey>()
//                     deserializedItem.key.qualifiedName shouldBe UnregisteredKey.qualifiedName
//                     deserializedItem.revision shouldBe 0
//                     deserializedItem.value should beInstanceOf<ByteArray>()
//                 }
//             }
//
//             When("Serializing an item unsupported by serializer") {
//                 val setupItem = Session.Context.Item(UnregisteredKey, 0, someValue)
//                 val setupString = coder.encodeToString(serializer, setupItem)
//                 val setupDeserializedItem = coder.decodeFromString(serializer, setupString)
//                 val item = Session.Context.Item(UnsupportedKey(RegisteredKey.qualifiedName), 0, setupDeserializedItem.value as ByteArray)
//                 val string = coder.encodeToString(serializer, item)
//
//                 Then("Is deserialized as RegisteredKey with correct type") {
//                     val deserializedItem = coder.decodeFromString(serializer, string)
//                     deserializedItem.key shouldBe RegisteredKey
//                     deserializedItem.revision shouldBe 0
//                     deserializedItem.value shouldBe someValue
//                 }
//             }
//         }
//     }
//
// })