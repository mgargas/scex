package com.avsystem.scex
package compiler

import scala.language.dynamics

/**
 * Created: 8/8/13
 * Author: ghik
 */
object SomeDynamic extends Dynamic {
  def selectDynamic(attr: String) = attr
}
