package com.avsystem.scex

import java.{util => ju, lang => jl}
import scala.reflect.runtime.{universe => ru}
import reflect.api.{Universe, TypeCreator}
import com.google.common.cache.CacheBuilder

class CachingTypeCreator(typeCreator: TypeCreator, typeRepr: String) {

  import CacheImplicits._

  // a single-entry cache (universe passed to typeIn will probably always be the compiler)
  private val cache = CacheBuilder.newBuilder.weakKeys.maximumSize(1)
    .initialCapacity(1).build[Universe, Universe#Type]

  def typeIn(u: Universe): u.Type =
    cache.get(u, u.TypeTag[Any](u.rootMirror, typeCreator).tpe).asInstanceOf[u.Type]

  override def toString = s"CachingTypeCreator($typeRepr)"
}