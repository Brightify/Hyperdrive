package org.brightify.hyperdrive

/**
 * Enables automatic factory generation for the annotated class, or a class containing the annotated constructor.
 *
 * When a class is annotated, a primary constructor is used to create a new instance of the class by the generated factory.
 * When a constructor is annotated, it's used to create a new instance of the class by the generated factory.
 *
 * Annotating multiple constructors or both the class and a constructor is not supported and will produce compilation errors.
 *
 * ## Future plans:
 * - The default name of the generated factory is `Factory`, but can be changed by providing a different value to the `factoryName`
 *   constructor parameter.
 * - Default parameter values.
 * - Generics.
 */
@Target(AnnotationTarget.CLASS, AnnotationTarget.CONSTRUCTOR)
@Retention(AnnotationRetention.SOURCE)
public annotation class AutoFactory(/*val factoryName: String = "Factory"*/)
