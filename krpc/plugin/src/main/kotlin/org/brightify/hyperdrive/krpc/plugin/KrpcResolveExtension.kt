package org.brightify.hyperdrive.krpc.plugin

import org.brightify.hyperdrive.krpc.plugin.util.containingClass
import org.brightify.hyperdrive.krpc.plugin.util.isContainingClassKrpcEnabled
import org.brightify.hyperdrive.krpc.plugin.util.isKrpcClient
import org.brightify.hyperdrive.krpc.plugin.util.isKrpcDescriptor
import org.brightify.hyperdrive.krpc.plugin.util.isKrpcDescriptorCall
import org.brightify.hyperdrive.krpc.plugin.util.isKrpcEnabled
import org.jetbrains.kotlin.descriptors.CallableMemberDescriptor
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.ClassDescriptorWithResolutionScopes
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.descriptors.PropertyDescriptor
import org.jetbrains.kotlin.descriptors.SimpleFunctionDescriptor
import org.jetbrains.kotlin.descriptors.SourceElement
import org.jetbrains.kotlin.descriptors.annotations.Annotations
import org.jetbrains.kotlin.descriptors.findClassAcrossModuleDependencies
import org.jetbrains.kotlin.descriptors.impl.ClassConstructorDescriptorImpl
import org.jetbrains.kotlin.descriptors.impl.PropertyDescriptorImpl
import org.jetbrains.kotlin.descriptors.impl.PropertyGetterDescriptorImpl
import org.jetbrains.kotlin.descriptors.impl.SimpleFunctionDescriptorImpl
import org.jetbrains.kotlin.descriptors.impl.ValueParameterDescriptorImpl
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.synthetics.SyntheticClassOrObjectDescriptor
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.calls.inference.returnTypeOrNothing
import org.jetbrains.kotlin.resolve.descriptorUtil.builtIns
import org.jetbrains.kotlin.resolve.descriptorUtil.module
import org.jetbrains.kotlin.resolve.extensions.SyntheticResolveExtension
import org.jetbrains.kotlin.resolve.lazy.LazyClassContext
import org.jetbrains.kotlin.resolve.lazy.declarations.ClassMemberDeclarationProvider
import org.jetbrains.kotlin.resolve.lazy.descriptors.LazyClassDescriptor
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.KotlinTypeFactory
import org.jetbrains.kotlin.types.SimpleType
import org.jetbrains.kotlin.types.Variance
import org.jetbrains.kotlin.types.typeUtil.createProjection
import org.jetbrains.kotlin.types.typeUtil.isSubtypeOf
import org.jetbrains.kotlin.types.typeUtil.replaceArgumentsWithStarProjections
import java.util.*

open class KrpcResolveExtension: SyntheticResolveExtension {
    companion object {
        val nestedClassNames = listOf(
            KnownType.Nested.client,
            KnownType.Nested.descriptor
        )

        val descriptorNestedClassNames = listOf(
            KnownType.Nested.call
        )

        val allPossibleNestedClassNames = nestedClassNames + descriptorNestedClassNames
    }

    override fun getSyntheticNestedClassNames(thisDescriptor: ClassDescriptor): List<Name> = when {
        thisDescriptor.isKrpcEnabled -> nestedClassNames
        thisDescriptor.isKrpcDescriptor -> descriptorNestedClassNames
        else -> emptyList()
    }

    override fun getPossibleSyntheticNestedClassNames(thisDescriptor: ClassDescriptor): List<Name> = allPossibleNestedClassNames

    override fun getSyntheticPropertiesNames(thisDescriptor: ClassDescriptor): List<Name> {
        return when {
            thisDescriptor.isKrpcDescriptor -> {
                listOf(KnownType.Nested.Descriptor.serviceIdentifier)
            }
            thisDescriptor.isKrpcDescriptorCall -> {
                val krpcEnabled = thisDescriptor.containingClass?.containingClass ?: return emptyList()
                krpcEnabled.unsubstitutedMemberScope.getFunctionNames().toList()
            }
            else -> emptyList()
        }
    }

    override fun getSyntheticFunctionNames(thisDescriptor: ClassDescriptor): List<Name> {
        return when {
            thisDescriptor.isKrpcClient -> {
                val krpcEnabled = thisDescriptor.containingClass ?: return emptyList()
                krpcEnabled.unsubstitutedMemberScope.getFunctionNames().toList()
            }
            thisDescriptor.isKrpcDescriptor -> {
                listOf(KnownType.Nested.Descriptor.describe)
            }
            else -> emptyList()
        }
    }

    override fun addSyntheticSupertypes(thisDescriptor: ClassDescriptor, supertypes: MutableList<KotlinType>) {
        super.addSyntheticSupertypes(thisDescriptor, supertypes)

        if (thisDescriptor.isContainingClassKrpcEnabled) {
            when {
                thisDescriptor.isKrpcClient ->
                    supertypes.add(thisDescriptor.containingClass?.defaultType ?: return)
                thisDescriptor.isKrpcDescriptor -> {
                    val type = KotlinTypeFactory.simpleNotNullType(
                        Annotations.EMPTY,
                        thisDescriptor.module.findClassAcrossModuleDependencies(KnownType.API.serviceDescriptor) ?: return,
                        listOf(createProjection(thisDescriptor.containingClass?.defaultType ?: return, Variance.INVARIANT, null))
                    )
                    supertypes.add(type)
                }
            }
        }
    }

    override fun generateSyntheticClasses(
        thisDescriptor: ClassDescriptor,
        name: Name,
        ctx: LazyClassContext,
        declarationProvider: ClassMemberDeclarationProvider,
        result: MutableSet<ClassDescriptor>
    ) {
        val thisDeclaration = declarationProvider.correspondingClassOrObject ?: return
        val scope = declarationProvider.ownerInfo?.let {
            ctx.declarationScopeProvider.getResolutionScopeForDeclaration(it.scopeAnchor)
        } ?: (thisDescriptor as ClassDescriptorWithResolutionScopes).scopeForClassHeaderResolution

        val isKrpcClient = name == KnownType.Nested.client && thisDescriptor.isKrpcEnabled
        val isKrpcDescriptor = name == KnownType.Nested.descriptor && thisDescriptor.isKrpcEnabled
        val isKrpcDescriptorCall = name == KnownType.Nested.call && thisDescriptor.isKrpcDescriptor

        // Quick-return if we're not generating a kRPC class.
        if (!(isKrpcClient || isKrpcDescriptorCall || isKrpcDescriptor)) {
            return
        }

        val cls = SyntheticClassOrObjectDescriptor(
            ctx,
            thisDeclaration,
            thisDescriptor,
            name,
            thisDescriptor.source,
            scope,
            Modality.FINAL,
            DescriptorVisibilities.PUBLIC,
            Annotations.EMPTY,
            DescriptorVisibilities.PRIVATE,
            if (isKrpcClient) ClassKind.CLASS else ClassKind.OBJECT,
            isCompanionObject = false
        )
        cls.initialize(emptyList())

        when {
            isKrpcClient -> {
                cls.secondaryConstructors = listOf(
                    ClassConstructorDescriptorImpl.create(
                        cls,
                        Annotations.EMPTY,
                        false,
                        cls.source
                    ).apply {
                        initialize(
                            listOf(
                                ValueParameterDescriptorImpl(
                                    this,
                                    null,
                                    0,
                                    Annotations.EMPTY,
                                    Name.identifier("transport"),
                                    thisDescriptor.module.findClassAcrossModuleDependencies(KnownType.API.transportClassId)?.defaultType ?: return,
                                    declaresDefaultValue = false,
                                    isCrossinline = false,
                                    isNoinline = false,
                                    varargElementType = null,
                                    source = this.source
                                )
                            ),
                            DescriptorVisibilities.PUBLIC
                        )
                        this.returnType = thisDescriptor.defaultType
                    }
                )
            }
            isKrpcDescriptor -> {

            }
            isKrpcDescriptorCall -> {

            }
        }

        result.add(cls)
    }

    override fun generateSyntheticMethods(
        thisDescriptor: ClassDescriptor,
        name: Name,
        bindingContext: BindingContext,
        fromSupertypes: List<SimpleFunctionDescriptor>,
        result: MutableCollection<SimpleFunctionDescriptor>
    ) {
        val createdFun = when {
            thisDescriptor.isKrpcClient -> {
                val krpcEnabledClass = thisDescriptor.containingClass as? LazyClassDescriptor ?: return
                val member = krpcEnabledClass.declaredCallableMembers.firstOrNull { it.name == name } ?: return

                val funDescriptor = SimpleFunctionDescriptorImpl.create(
                    thisDescriptor,
                    Annotations.EMPTY,
                    name,
                    CallableMemberDescriptor.Kind.SYNTHESIZED,
                    member.source
                )

                funDescriptor.initialize(
                    null,
                    thisDescriptor.thisAsReceiverParameter,
                    emptyList(),
                    member.valueParameters.mapIndexed { index, parameter ->
                        ValueParameterDescriptorImpl(
                            funDescriptor,
                            null,
                            index,
                            Annotations.EMPTY,
                            parameter.name,
                            parameter.type,
                            declaresDefaultValue = parameter.declaresDefaultValue(),
                            isCrossinline = parameter.isCrossinline,
                            isNoinline = parameter.isNoinline,
                            varargElementType = parameter.varargElementType,
                            source = parameter.source
                        )
                    },
                    member.returnType,
                    Modality.FINAL,
                    DescriptorVisibilities.PUBLIC
                )

                funDescriptor.isSuspend = true
                funDescriptor
            }
            thisDescriptor.isKrpcDescriptor -> {
                val krpcEnabledClass = thisDescriptor.containingClass as? LazyClassDescriptor ?: return

                if (name != KnownType.Nested.Descriptor.describe) { return }

                val funDescriptor = SimpleFunctionDescriptorImpl.create(
                    thisDescriptor,
                    Annotations.EMPTY,
                    name,
                    CallableMemberDescriptor.Kind.SYNTHESIZED,
                    thisDescriptor.source
                )

                funDescriptor.initialize(
                    null,
                    thisDescriptor.thisAsReceiverParameter,
                    emptyList(),
                    listOf(
                        ValueParameterDescriptorImpl(
                            funDescriptor,
                            null,
                            0,
                            Annotations.EMPTY,
                            Name.identifier("service"),
                            krpcEnabledClass.defaultType,
                            declaresDefaultValue = false,
                            isCrossinline = false,
                            isNoinline = false,
                            varargElementType = null,
                            source = funDescriptor.source
                        )
                    ),
                    thisDescriptor.module.findClassAcrossModuleDependencies(KnownType.API.serviceDescription)?.defaultType ?: return,
                    Modality.FINAL,
                    DescriptorVisibilities.PUBLIC
                )

                funDescriptor
            }
            thisDescriptor.isKrpcDescriptorCall -> {
                return
            }
            else -> return
        }

        result.add(createdFun)
    }

    override fun generateSyntheticProperties(
        thisDescriptor: ClassDescriptor,
        name: Name,
        bindingContext: BindingContext,
        fromSupertypes: ArrayList<PropertyDescriptor>,
        result: MutableSet<PropertyDescriptor>
    ) {
        if (thisDescriptor.isKrpcDescriptorCall) {
            val krpcEnabledClass = thisDescriptor.containingClass?.containingClass as? LazyClassDescriptor ?: return
            val member = krpcEnabledClass.declaredCallableMembers.firstOrNull { it.name == name } ?: return
            val descriptorType = thisDescriptor.module.callDescriptorPropertyType(member) ?: return

            val propDescriptor = PropertyDescriptorImpl.create(
                thisDescriptor,
                Annotations.EMPTY,
                Modality.FINAL,
                DescriptorVisibilities.PUBLIC,
                false,
                name,
                CallableMemberDescriptor.Kind.SYNTHESIZED,
                member.source,
                false,
                false,
                false,
                false,
                false,
                false
            )

            propDescriptor.initialize(
                PropertyGetterDescriptorImpl(
                    propDescriptor,
                    Annotations.EMPTY,
                    Modality.FINAL,
                    DescriptorVisibilities.PUBLIC,
                    false,
                    false,
                    false,
                    CallableMemberDescriptor.Kind.SYNTHESIZED,
                    null,
                    propDescriptor.source
                ).apply {
                       initialize(
                           descriptorType
                       )
                },
                null
            )

            propDescriptor.setType(
                descriptorType, emptyList(), thisDescriptor.thisAsReceiverParameter, null
            )

            result.add(propDescriptor)
        } else if (thisDescriptor.isKrpcDescriptor && name == KnownType.Nested.Descriptor.serviceIdentifier) {
            val propDescriptor = PropertyDescriptorImpl.create(
                thisDescriptor,
                Annotations.EMPTY,
                Modality.FINAL,
                DescriptorVisibilities.PUBLIC,
                false,
                name,
                CallableMemberDescriptor.Kind.SYNTHESIZED,
                SourceElement.NO_SOURCE,
                false,
                false,
                false,
                false,
                false,
                false
            )

            propDescriptor.initialize(
                PropertyGetterDescriptorImpl(
                    propDescriptor,
                    Annotations.EMPTY,
                    Modality.FINAL,
                    DescriptorVisibilities.PUBLIC,
                    false,
                    false,
                    false,
                    CallableMemberDescriptor.Kind.SYNTHESIZED,
                    null,
                    propDescriptor.source
                ).apply {
                    initialize(thisDescriptor.builtIns.stringType)
                },
                null
            )
            propDescriptor.setType(
                thisDescriptor.builtIns.stringType, emptyList(), thisDescriptor.thisAsReceiverParameter, null
            )

            result.add(propDescriptor)
        }
    }

    override fun getSyntheticCompanionObjectNameIfNeeded(thisDescriptor: ClassDescriptor): Name? = null

    private fun ModuleDescriptor.callDescriptorPropertyType(member: CallableMemberDescriptor): SimpleType? {
        val flow = findClassAcrossModuleDependencies(ClassId.topLevel(KnownType.Coroutines.flow)) ?: return null

        val isServerStream = member.returnTypeOrNothing.isSubtypeOf(flow.defaultType.replaceArgumentsWithStarProjections())
        val isClientStream = member.valueParameters.lastOrNull()?.type?.isSubtypeOf(flow.defaultType.replaceArgumentsWithStarProjections()) ?: false
        val payloadParameters = if (isClientStream) member.valueParameters.dropLast(1) else member.valueParameters

        val requestWrapperProjection = createProjection(
            KotlinTypeFactory.simpleNotNullType(
                Annotations.EMPTY,
                KnownType.API.requestWrapper(payloadParameters.count()).asClassDescriptor(this),
                payloadParameters.map {
                    createProjection(it.type, Variance.INVARIANT, null)
                }
            ),
            Variance.INVARIANT,
            null
        )

        return when {
            isServerStream && isClientStream -> {
                KotlinTypeFactory.simpleNotNullType(
                    Annotations.EMPTY,
                    KnownType.API.coldBistreamCallDescription.asClassDescriptor(this),
                    listOf(
                        requestWrapperProjection,
                        createProjection(member.valueParameters.last().type.arguments.first().type, Variance.INVARIANT, null),
                        createProjection(member.returnType!!.arguments.first().type, Variance.INVARIANT, null)
                    )
                )
            }
            isClientStream -> {
                KotlinTypeFactory.simpleNotNullType(
                    Annotations.EMPTY,
                    KnownType.API.coldUpstreamCallDescription.asClassDescriptor(this),
                    listOf(
                        requestWrapperProjection,
                        createProjection(member.valueParameters.last().type.arguments.first().type, Variance.INVARIANT, null),
                        createProjection(member.returnType!!, Variance.INVARIANT, null)
                    )
                )
            }
            isServerStream -> {
                KotlinTypeFactory.simpleNotNullType(
                    Annotations.EMPTY,
                    KnownType.API.coldDownstreamCallDescription.asClassDescriptor(this),
                    listOf(
                        requestWrapperProjection,
                        createProjection(member.returnType!!.arguments.first().type, Variance.INVARIANT, null)
                    )
                )
            }
            else -> {
                KotlinTypeFactory.simpleNotNullType(
                    Annotations.EMPTY,
                    KnownType.API.singleCallDescription.asClassDescriptor(this),
                    listOf(
                        requestWrapperProjection,
                        createProjection(member.returnType!!, Variance.INVARIANT, null)
                    )
                )
            }
        }
    }

    private fun FqName.asClassDescriptor(module: ModuleDescriptor): ClassDescriptor {
        return requireNotNull(module.findClassAcrossModuleDependencies(ClassId.topLevel(this))) {
            "Could not find class $this in module $module."
        }
    }
}
