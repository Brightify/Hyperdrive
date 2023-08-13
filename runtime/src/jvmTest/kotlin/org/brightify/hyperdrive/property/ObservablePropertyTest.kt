package org.brightify.hyperdrive.property

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.property.checkAll
import org.brightify.hyperdrive.Logger
import org.brightify.hyperdrive.LoggingLevel
import org.brightify.hyperdrive.PrintlnDestination
import org.brightify.hyperdrive.property.impl.ValueObservableProperty

class ObservablePropertyTest: BehaviorSpec({
    Logger.configure {
        setMinLevel(LoggingLevel.Trace)
        destination(PrintlnDestination())
    }

    Given("A boolean property") {
        val property = MutableObservableProperty(true)

        When("Setting a new value") {
            property.value = false

            Then("The value is changed") {
                property.value.shouldBeFalse()
            }
        }

        And("Accessing its value in map transformation") {
            val mappedProperty = property.map {
                property.value
            }

            When("Setting a new value") {
                property.value = false

                Then("The mapped value is changed") {
                    mappedProperty.value.shouldBeFalse()
                }
            }
        }
    }
}) {
    override fun isolationMode(): IsolationMode = IsolationMode.InstancePerLeaf
}
