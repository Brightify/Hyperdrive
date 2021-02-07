package org.brightify.hyperdrive

import org.jetbrains.kotlin.descriptors.CallableMemberDescriptor
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.ConstructorDescriptor
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.descriptors.SimpleFunctionDescriptor
import org.jetbrains.kotlin.descriptors.ValueParameterDescriptor
import org.jetbrains.kotlin.descriptors.annotations.Annotations
import org.jetbrains.kotlin.descriptors.impl.ClassConstructorDescriptorImpl
import org.jetbrains.kotlin.descriptors.impl.SimpleFunctionDescriptorImpl
import org.jetbrains.kotlin.descriptors.impl.ValueParameterDescriptorImpl
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrConstructor
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOriginImpl
import org.jetbrains.kotlin.ir.util.constructors
import org.jetbrains.kotlin.ir.util.hasAnnotation
import org.jetbrains.kotlin.ir.util.primaryConstructor
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.synthetics.SyntheticClassOrObjectDescriptor
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.descriptorUtil.module
import org.jetbrains.kotlin.resolve.extensions.SyntheticResolveExtension
import org.jetbrains.kotlin.resolve.lazy.LazyClassContext
import org.jetbrains.kotlin.resolve.lazy.declarations.ClassMemberDeclarationProvider
import org.jetbrains.kotlin.types.KotlinType

val ClassDescriptor.autoFactoryConstructor: ConstructorDescriptor?
    get() = constructors.firstOrNull { it.annotations.hasAnnotation(AutoFactoryNames.Annotation.autoFactory) } ?:
        if (annotations.hasAnnotation(AutoFactoryNames.Annotation.autoFactory)) {
            unsubstitutedPrimaryConstructor
        } else {
            null
        }

val IrClass.parentAutoFactoryConstructor: IrConstructor?
    get() = when (val parent = parent) {
        is IrClass -> {
            parent.constructors.firstOrNull { it.annotations.hasAnnotation(AutoFactoryNames.Annotation.autoFactory) } ?:
            if (parent.hasAnnotation(AutoFactoryNames.Annotation.autoFactory)) {
                parent.primaryConstructor
            } else {
                null
            }
        }
        else -> null
    }

val ConstructorDescriptor.injectedValueParameters: List<ValueParameterDescriptor>
    get() = valueParameters.filterNot { it.annotations.hasAnnotation(AutoFactoryNames.Annotation.provided) }

val ConstructorDescriptor.providedValueParameters: List<ValueParameterDescriptor>
    get() = valueParameters.filter { it.annotations.hasAnnotation(AutoFactoryNames.Annotation.provided) }

open class AutoFactoryResolveExtension: SyntheticResolveExtension {
    override fun getSyntheticNestedClassNames(thisDescriptor: ClassDescriptor): List<Name> = if (
        thisDescriptor.annotations.hasAnnotation(AutoFactoryNames.Annotation.autoFactory) ||
        thisDescriptor.constructors.any { it.isPrimary && it.annotations.hasAnnotation(AutoFactoryNames.Annotation.autoFactory) })
    {
        listOf(AutoFactoryNames.factory)
    } else {
        emptyList()
    }

    override fun getPossibleSyntheticNestedClassNames(thisDescriptor: ClassDescriptor): List<Name>? {
        return listOf(AutoFactoryNames.factory)
    }

    override fun getSyntheticFunctionNames(thisDescriptor: ClassDescriptor): List<Name> = if (thisDescriptor.name == AutoFactoryNames.factory) {
        listOf(AutoFactoryNames.createFun)
    } else {
        emptyList()
    }

    override fun generateSyntheticClasses(
        thisDescriptor: ClassDescriptor,
        name: Name,
        ctx: LazyClassContext,
        declarationProvider: ClassMemberDeclarationProvider,
        result: MutableSet<ClassDescriptor>
    ) {
        if (name != AutoFactoryNames.factory) { return }
        val autoFactoryConstructor = thisDescriptor.autoFactoryConstructor ?: return

        val thisDeclaration = declarationProvider.correspondingClassOrObject!!
        val scope = ctx.declarationScopeProvider.getResolutionScopeForDeclaration(declarationProvider.ownerInfo!!.scopeAnchor)

        val injectedValueParameters = autoFactoryConstructor.injectedValueParameters

        val factoryDescriptor = SyntheticClassOrObjectDescriptor(
            ctx,
            thisDeclaration,
            thisDescriptor,
            name,
            thisDescriptor.source,
            scope,
            Modality.FINAL,
            DescriptorVisibilities.PUBLIC,
            Annotations.EMPTY,
            if (injectedValueParameters.isEmpty()) DescriptorVisibilities.PUBLIC else DescriptorVisibilities.PRIVATE,
            ClassKind.CLASS,
            false
        )
        factoryDescriptor.initialize(emptyList())

        if (injectedValueParameters.isNotEmpty()) {
            factoryDescriptor.secondaryConstructors = listOf(
                ClassConstructorDescriptorImpl.create(
                    factoryDescriptor,
                    Annotations.EMPTY,
                    false,
                    factoryDescriptor.source
                ).apply {
                    initialize(
                        injectedValueParameters
                            .mapIndexed { index, parameter ->
                                ValueParameterDescriptorImpl(
                                    this,
                                    null,
                                    index,
                                    Annotations.EMPTY,
                                    parameter.name,
                                    parameter.type,
                                    declaresDefaultValue = false,
                                    isCrossinline = false,
                                    isNoinline = false,
                                    varargElementType = null,
                                    source = this.source
                                )
                            },
                        DescriptorVisibilities.PUBLIC
                    )
                    this.returnType = factoryDescriptor.defaultType
                }
            )
        }

        result.add(factoryDescriptor)
    }

    override fun addSyntheticSupertypes(thisDescriptor: ClassDescriptor, supertypes: MutableList<KotlinType>) {
        super.addSyntheticSupertypes(thisDescriptor, supertypes)

        if (thisDescriptor.name == AutoFactoryNames.factory) {
            supertypes.add(thisDescriptor.module.builtIns.anyType)
        }
    }

    override fun generateSyntheticMethods(
        thisDescriptor: ClassDescriptor,
        name: Name,
        bindingContext: BindingContext,
        fromSupertypes: List<SimpleFunctionDescriptor>,
        result: MutableCollection<SimpleFunctionDescriptor>
    ) {
        super.generateSyntheticMethods(thisDescriptor, name, bindingContext, fromSupertypes, result)

        if (name != AutoFactoryNames.createFun) { return }

        val containingClass = thisDescriptor.containingDeclaration as? ClassDescriptor ?: return
        val autoFactoryConstructor = containingClass.autoFactoryConstructor ?: return

        val createFunDescriptor = SimpleFunctionDescriptorImpl.create(
            thisDescriptor,
            Annotations.EMPTY,
            name,
            CallableMemberDescriptor.Kind.SYNTHESIZED,
            thisDescriptor.source
        )

        createFunDescriptor.initialize(
            null,
            thisDescriptor.thisAsReceiverParameter,
            emptyList(),
            autoFactoryConstructor.valueParameters
                .filter { it.annotations.hasAnnotation(AutoFactoryNames.Annotation.provided) }
                .mapIndexed { index, parameter ->
                    ValueParameterDescriptorImpl(
                        createFunDescriptor,
                        null,
                        index,
                        Annotations.EMPTY,
                        parameter.name,
                        parameter.type,
                        declaresDefaultValue = false,
                        isCrossinline = false,
                        isNoinline = false,
                        varargElementType = null,
                        source = createFunDescriptor.source
                    )
                },
            containingClass.defaultType,
            Modality.FINAL,
            DescriptorVisibilities.PUBLIC
        )

        result.add(createFunDescriptor)
    }

    override fun getSyntheticCompanionObjectNameIfNeeded(thisDescriptor: ClassDescriptor): Name? {
        return null
    }

    override fun getSyntheticPropertiesNames(thisDescriptor: ClassDescriptor): List<Name> {
        return emptyList()
    }
}

object AUTO_FACTORY_PLUGIN_ORIGIN : IrDeclarationOriginImpl("AUTO_FACTORY", true)

