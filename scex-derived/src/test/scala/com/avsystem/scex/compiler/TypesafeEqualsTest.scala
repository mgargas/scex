package com.avsystem.scex.compiler

import com.avsystem.scex.compiler.ScexCompiler.CompilationFailedException
import com.avsystem.scex.validation.{SymbolValidator, SyntaxValidator}
import com.avsystem.scex.{ExpressionProfile, PredefinedAccessSpecs}
import java.{util => ju, lang => jl}
import org.scalatest.FunSuite
import scala.reflect.runtime.universe.TypeTag
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner

/**
 * Created: 20-11-2013
 * Author: ghik
 */
@RunWith(classOf[JUnitRunner])
class TypesafeEqualsTest extends FunSuite with CompilationTest {

  import com.avsystem.scex.validation.SymbolValidator._

  override def evaluate[T: TypeTag](expr: String, acl: List[MemberAccessSpec] = PredefinedAccessSpecs.basicOperations) = {
    val profile = new ExpressionProfile(SyntaxValidator.SimpleExpressions, SymbolValidator(acl),
      "import com.avsystem.scex.util.TypesafeEquals._", "")

    compiler.getCompiledExpression[SimpleContext[Unit], T](profile, expr, template = false).apply(SimpleContext(()))
  }

  test("same type equality test") {
    assert(true === evaluate[Boolean]("\"lol\" == \"lol\""))
    assert(false === evaluate[Boolean]("\"lol\" == \"lol2\""))
  }

  test("same hierarchy equality test") {
    val acl = PredefinedAccessSpecs.basicOperations ++ allow(new Object)

    assert(false === evaluate[Boolean]("\"somestring\" == new Object", acl))
    assert(false === evaluate[Boolean]("new Object == \"somestring\"", acl))
  }

  test("implicit-conversion based equality test") {
    val acl = PredefinedAccessSpecs.basicOperations ++ allow(intWrapper _)

    assert(true === evaluate[Boolean]("1 == (1: scala.runtime.RichInt)", acl))
    assert(true === evaluate[Boolean]("(1: scala.runtime.RichInt) == 1", acl))
  }

  test("unrelated types test") {
    val exception = intercept[CompilationFailedException] {
      evaluate[Boolean]("1 == \"somestring\"")
    }
    assert(exception.errors.head.msg === "Values of types Int and String cannot be compared")
  }

  test("literals test") {
    assert(true === evaluate[Boolean]("com.avsystem.scex.util.Literal(\"42\") == 42"))
    assert(true === evaluate[Boolean]("42 == com.avsystem.scex.util.Literal(\"42\")"))
  }

}