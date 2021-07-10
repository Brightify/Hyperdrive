package org.brightify.hyperdrive.viewmodel

import org.jetbrains.kotlin.descriptors.CallableMemberDescriptor
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.descriptors.PropertyDescriptor
import org.jetbrains.kotlin.descriptors.annotations.Annotations
import org.jetbrains.kotlin.descriptors.findClassAcrossModuleDependencies
import org.jetbrains.kotlin.descriptors.impl.PropertyDescriptorImpl
import org.jetbrains.kotlin.descriptors.impl.PropertyGetterDescriptorImpl
import org.jetbrains.kotlin.incremental.components.NoLookupLocation
import org.jetbrains.kotlin.js.resolve.diagnostics.findPsi
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.calls.inference.returnTypeOrNothing
import org.jetbrains.kotlin.resolve.descriptorUtil.module
import org.jetbrains.kotlin.resolve.extensions.SyntheticResolveExtension
import org.jetbrains.kotlin.types.KotlinTypeFactory
import org.jetbrains.kotlin.types.Variance
import org.jetbrains.kotlin.types.typeUtil.createProjection

open class ViewModelResolveExtension: SyntheticResolveExtension {
    private companion object {
        val observableDelegates = listOf(
            "published",
            "collected",
            "collectedFlatMap",
            "binding",
            "managed",
            "managedList",
        )
    }

    override fun getSyntheticNestedClassNames(thisDescriptor: ClassDescriptor): List<Name> = emptyList()

    override fun getPossibleSyntheticNestedClassNames(thisDescriptor: ClassDescriptor): List<Name>? = emptyList()

    override fun getSyntheticFunctionNames(thisDescriptor: ClassDescriptor): List<Name> = emptyList()

    // TODO: Not supported yet. :(
    // override fun addSyntheticSupertypes(thisDescriptor: ClassDescriptor, supertypes: MutableList<KotlinType>) {
    //     super.addSyntheticSupertypes(thisDescriptor, supertypes)
    //
    //
    //    if (thisDescriptor.annotations.hasAnnotation(ViewModelNames.Annotation.viewModel)) {
    //        supertypes.add(
    //            thisDescriptor.module.findClassAcrossModuleDependencies(ViewModelNames.API.baseViewModel)!!.defaultType
    //        )
    //    }
    // }

    override fun generateSyntheticProperties(
        thisDescriptor: ClassDescriptor,
        name: Name,
        bindingContext: BindingContext,
        fromSupertypes: ArrayList<PropertyDescriptor>,
        result: MutableSet<PropertyDescriptor>
    ) {
        super.generateSyntheticProperties(thisDescriptor, name, bindingContext, fromSupertypes, result)

        if (!thisDescriptor.annotations.hasAnnotation(ViewModelNames.Annotation.viewModel)) { return }
        if (result.isNotEmpty() || fromSupertypes.isNotEmpty()) {
            return
        }
        val stateFlow = thisDescriptor.module.findClassAcrossModuleDependencies(ViewModelNames.Coroutines.stateFlowClassId) ?: return
        val mutableStateFlow = thisDescriptor.module.findClassAcrossModuleDependencies(ViewModelNames.Coroutines.mutableStateFlowClassId) ?: return

        val referencedPropertyIdentifier = NamingHelper.getReferencedPropertyName(name.identifier) ?: return

        val refName = Name.identifier(referencedPropertyIdentifier)
        val realDescriptor = thisDescriptor.unsubstitutedMemberScope
            .getContributedVariables(refName, NoLookupLocation.FROM_SYNTHETIC_SCOPE)
            .singleOrNull() ?: return

        val delegateCallExpression = (realDescriptor.findPsi() as? KtProperty)?.delegateExpression
        if (delegateCallExpression != null && observableDelegates.any { delegateCallExpression.text.startsWith("$it(") })
            result.add(
                PropertyDescriptorImpl.create(
                    thisDescriptor,
                    Annotations.EMPTY,
                    Modality.FINAL,
                    realDescriptor.visibility,
                    false,
                    name,
                    CallableMemberDescriptor.Kind.SYNTHESIZED,
                    realDescriptor.source,
                    false,
                    false,
                    false,
                    false,
                    false,
                    false
                ).apply {
                    val type = KotlinTypeFactory.simpleNotNullType(
                        Annotations.EMPTY,
                        if (realDescriptor.isVar) mutableStateFlow else stateFlow,
                        listOf(createProjection(realDescriptor.returnTypeOrNothing, Variance.INVARIANT, null))
                    )

                    initialize(
                        PropertyGetterDescriptorImpl(
                            this,
                            Annotations.EMPTY,
                            Modality.FINAL,
                            this.visibility,
                            false,
                            false,
                            false,
                            CallableMemberDescriptor.Kind.SYNTHESIZED,
                            null,
                            source
                        ).apply {
                            initialize(type)
                        },
                        null
                    )
                    setType(type, emptyList(), thisDescriptor.thisAsReceiverParameter, null)
                }
            )
    }

    override fun getSyntheticPropertiesNames(thisDescriptor: ClassDescriptor): List<Name> {
        return thisDescriptor.unsubstitutedMemberScope.getClassifierNames()?.filter {
            val realDescriptor =
                thisDescriptor.unsubstitutedMemberScope.getContributedVariables(it, NoLookupLocation.FROM_SYNTHETIC_SCOPE)
                    .singleOrNull() ?: return@filter false
            val delegateCallExpression = (realDescriptor.findPsi() as? KtProperty)?.delegateExpression
            delegateCallExpression != null && observableDelegates.any { delegateCallExpression.text.startsWith("$it(") }
        }?.map {
            Name.identifier(NamingHelper.getObservingPropertyName(it.identifier))
        } ?: emptyList()
    }

    override fun getSyntheticCompanionObjectNameIfNeeded(thisDescriptor: ClassDescriptor): Name? {
        return null
    }
}
