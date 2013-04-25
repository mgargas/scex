package com.avsystem.scex

import java.{util => ju, lang => jl}
import scala.reflect.runtime.{universe => ru}
import com.google.common.cache.{RemovalNotification, RemovalListener, CacheLoader}
import scala.language.implicitConversions
import java.util.concurrent.Callable

object CacheImplicits {
  implicit def funToCacheLoader[K, V](fun: K => V) =
    new CacheLoader[K, V] {
      def load(key: K): V = fun(key)
    }

  implicit def funToRemovalListener[K, V](fun: RemovalNotification[K, V] => Unit) =
    new RemovalListener[K, V] {
      def onRemoval(notification: RemovalNotification[K, V]) {
        fun(notification)
      }
    }

  implicit def exprToCallable[T](expr: => T) =
    new Callable[T] {
      def call(): T = expr
    }
}
