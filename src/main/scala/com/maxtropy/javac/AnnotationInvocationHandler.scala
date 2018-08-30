package com.maxtropy.javac

import java.lang.annotation.{Annotation, AnnotationFormatError}
import java.lang.reflect.{Array, InvocationHandler, Method}
import java.util

class AnnotationInvocationHandler[T <: Annotation](val clazz:Class[T],val valueMap:Map[String,AnyRef]) extends InvocationHandler{


  import AnnotationInvocationHandler._

  val interfaces = clazz.getInterfaces

  if (!(clazz.isAnnotation && interfaces.length == 1 && (interfaces(0) eq classOf[Annotation]))) {
    throw new AnnotationFormatError("Attempt to create proxy for a non-annotation type.")
  }



  override def invoke(proxy: scala.Any, method: Method, args: scala.Array[AnyRef]): AnyRef = {
    valueMap.get(method.getName) match {
      case Some(v) =>
        v
      case None =>
        val defaultValue = method.getDefaultValue
        if(defaultValue == null){ throw makeNoDefaultFail(method)}
        defaultValue
    }
  }


  /*
  override def invoke(proxy: scala.Any, method: Method, args: Array[AnyRef]): AnyRef = {

  }*/

}

object AnnotationInvocationHandler{

  private def makeNoDefaultFail(method: Method) = new AnnotationFormatError("No value supplied but " + method.getName + " has no default either.")
}
