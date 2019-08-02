package io.tanker.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

// Do not move or rename without updating the proguard rules files
// This instructs proguard to not remove fields with this annotation
// Useful when we need the GC to keep a reference to a variable that's only used in native code
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.CLASS)
@interface ProguardKeep { }
