package org.brightify.hyperdrive.viewmodel

import org.brightify.hyperdrive.autofactory.AutoFactoryNames
import org.brightify.hyperdrive.autofactory.autoFactoryConstructor
import org.brightify.hyperdrive.autofactory.injectedValueParameters
import org.jetbrains.kotlin.descriptors.CallableMemberDescriptor
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.descriptors.PackageFragmentDescriptor
import org.jetbrains.kotlin.descriptors.PropertyDescriptor
import org.jetbrains.kotlin.descriptors.SimpleFunctionDescriptor
import org.jetbrains.kotlin.descriptors.annotations.Annotations
import org.jetbrains.kotlin.descriptors.computeConstructorTypeParameters
import org.jetbrains.kotlin.descriptors.findClassAcrossModuleDependencies
import org.jetbrains.kotlin.descriptors.impl.ClassConstructorDescriptorImpl
import org.jetbrains.kotlin.descriptors.impl.PropertyDescriptorImpl
import org.jetbrains.kotlin.descriptors.impl.PropertyGetterDescriptorImpl
import org.jetbrains.kotlin.descriptors.impl.SimpleFunctionDescriptorImpl
import org.jetbrains.kotlin.descriptors.impl.ValueParameterDescriptorImpl
import org.jetbrains.kotlin.descriptors.resolveClassByFqName
import org.jetbrains.kotlin.incremental.components.NoLookupLocation
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOriginImpl
import org.jetbrains.kotlin.js.resolve.diagnostics.findPsi
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.synthetics.SyntheticClassOrObjectDescriptor
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.calls.inference.returnTypeOrNothing
import org.jetbrains.kotlin.resolve.descriptorUtil.denotedClassDescriptor
import org.jetbrains.kotlin.resolve.descriptorUtil.getSuperClassOrAny
import org.jetbrains.kotlin.resolve.descriptorUtil.module
import org.jetbrains.kotlin.resolve.extensions.SyntheticResolveExtension
import org.jetbrains.kotlin.resolve.lazy.LazyClassContext
import org.jetbrains.kotlin.resolve.lazy.declarations.ClassMemberDeclarationProvider
import org.jetbrains.kotlin.resolve.lazy.declarations.PackageMemberDeclarationProvider
import org.jetbrains.kotlin.resolve.lazy.descriptors.LazyClassDescriptor
import org.jetbrains.kotlin.resolve.scopes.DescriptorKindFilter
import org.jetbrains.kotlin.resolve.scopes.computeAllNames
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.KotlinTypeFactory
import org.jetbrains.kotlin.types.KotlinTypeRefinerImpl
import org.jetbrains.kotlin.types.Variance
import org.jetbrains.kotlin.types.typeUtil.asTypeProjection
import org.jetbrains.kotlin.types.typeUtil.createProjection
import java.util.ArrayList

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

    override fun addSyntheticSupertypes(thisDescriptor: ClassDescriptor, supertypes: MutableList<KotlinType>) {
        super.addSyntheticSupertypes(thisDescriptor, supertypes)

        // TODO: Not supported yet. :(
//        if (thisDescriptor.annotations.hasAnnotation(ViewModelNames.Annotation.viewModel)) {
//            supertypes.add(
//                thisDescriptor.module.findClassAcrossModuleDependencies(ViewModelNames.API.baseViewModel)!!.defaultType
//            )
//        }
    }

    override fun generateSyntheticClasses(
        thisDescriptor: PackageFragmentDescriptor,
        name: Name,
        ctx: LazyClassContext,
        declarationProvider: PackageMemberDeclarationProvider,
        result: MutableSet<ClassDescriptor>
    ) {
        super.generateSyntheticClasses(thisDescriptor, name, ctx, declarationProvider, result)
    }

    override fun generateSyntheticClasses(
        thisDescriptor: ClassDescriptor,
        name: Name,
        ctx: LazyClassContext,
        declarationProvider: ClassMemberDeclarationProvider,
        result: MutableSet<ClassDescriptor>
    ) {
        super.generateSyntheticClasses(thisDescriptor, name, ctx, declarationProvider, result)
    }

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

        if (name.identifier.startsWith("observe")) {
            val refIdentifier = name.identifier.drop("observe".count()).let {
                if (it.isEmpty()) {
                    null
                } else {
                    it.first().toLowerCase() + it.drop(1)
                }
            } ?: return
            val refName = Name.identifier(refIdentifier)
            val realDescriptor =
                thisDescriptor.unsubstitutedMemberScope.getContributedVariables(refName, NoLookupLocation.FROM_SYNTHETIC_SCOPE)
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
                            stateFlow,
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
    }

    override fun getSyntheticPropertiesNames(thisDescriptor: ClassDescriptor): List<Name> {
        return thisDescriptor.unsubstitutedMemberScope.getClassifierNames()?.filter {
            val realDescriptor =
                thisDescriptor.unsubstitutedMemberScope.getContributedVariables(it, NoLookupLocation.FROM_SYNTHETIC_SCOPE)
                    .singleOrNull() ?: return@filter false
            val delegateCallExpression = (realDescriptor.findPsi() as? KtProperty)?.delegateExpression
            delegateCallExpression != null && observableDelegates.any { delegateCallExpression.text.startsWith("$it(") }
        }?.map {
            Name.identifier("observe${it.identifier.capitalize()}")
        } ?: emptyList()
    }

    override fun generateSyntheticMethods(
        thisDescriptor: ClassDescriptor,
        name: Name,
        bindingContext: BindingContext,
        fromSupertypes: List<SimpleFunctionDescriptor>,
        result: MutableCollection<SimpleFunctionDescriptor>
    ) {
        super.generateSyntheticMethods(thisDescriptor, name, bindingContext, fromSupertypes, result)
    }

    override fun getSyntheticCompanionObjectNameIfNeeded(thisDescriptor: ClassDescriptor): Name? {
        return null
    }
}
