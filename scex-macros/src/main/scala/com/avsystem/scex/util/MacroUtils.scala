package com.avsystem.scex.util

import scala.reflect.api.Universe
import scala.util.matching.Regex

trait MacroUtils {
  val universe: Universe

  import universe._

  def scexClassType(suffix: String): Type =
    rootMirror.staticClass("com.avsystem.scex." + suffix).toType

  lazy val ScexPkg = q"_root_.com.avsystem.scex"

  lazy val adapterType: Type = scexClassType("compiler.Markers.JavaGetterAdapter")
  lazy val syntheticType: Type = scexClassType("compiler.Markers.Synthetic")
  lazy val expressionUtilType: Type = scexClassType("compiler.Markers.ExpressionUtil")
  lazy val profileObjectType: Type = scexClassType("compiler.Markers.ProfileObject")

  lazy val inputAnnotType: Type = scexClassType("compiler.annotation.Input")
  lazy val rootValueAnnotType: Type = scexClassType("compiler.annotation.RootValue")
  lazy val rootAdapterAnnotType: Type = scexClassType("compiler.annotation.RootAdapter")
  lazy val notValidatedAnnotType: Type = scexClassType("compiler.annotation.NotValidated")
  lazy val templateInterpolationsType: Type = scexClassType("compiler.TemplateInterpolations")
  lazy val splicerType: Type = scexClassType("compiler.TemplateInterpolations.Splicer")

  lazy val any2stringadd: Symbol = symAlternatives(typeOf[Predef.type].member(TermName("any2stringadd")))
    .find(_.isMethod).getOrElse(NoSymbol)
  lazy val stringAddPlus: Symbol = typeOf[any2stringadd[_]].member(TermName("+").encodedName)
  lazy val stringConcat: Symbol = typeOf[String].member(TermName("+").encodedName)
  lazy val safeToString: Symbol = templateInterpolationsType.companion.decl(TermName("safeToString"))
  lazy val splicerToString: Symbol = splicerType.decl(TermName("toString"))
  lazy val stringTpe: Type = typeOf[String]
  lazy val booleanTpe: Type = typeOf[Boolean]
  lazy val jBooleanTpe: Type = typeOf[java.lang.Boolean]
  lazy val dynamicTpe: Type = typeOf[Dynamic]
  lazy val dynamicVarAccessorTpe: Type = scexClassType("util.DynamicVariableAccessor")

  lazy val BeanGetterNamePattern: Regex = "get(([A-Z][a-z0-9_]*)+)".r
  lazy val BooleanBeanGetterNamePattern: Regex = "is(([A-Z][a-z0-9_]*)+)".r
  lazy val BeanSetterNamePattern: Regex = "set(([A-Z][a-z0-9_]*)+)".r
  lazy val AdapterWrappedName: TermName = TermName("_wrapped")

  lazy val toplevelSymbols: Set[Symbol] = Set(typeOf[Any], typeOf[AnyRef], typeOf[AnyVal]).map(_.typeSymbol)
  lazy val standardStringInterpolations: Set[Symbol] = Set("s", "raw").map(name => typeOf[StringContext].member(TermName(name)))
  lazy val getClassSymbol: Symbol = typeOf[Any].member(TermName("getClass"))

  object DecodedTermName {
    def unapply(name: TermName) =
      Some(name.decodedName.toString)
  }

  object DecodedTypeName {
    def unapply(name: TermName) =
      Some(name.decodedName.toString)
  }

  object LiteralString {
    def unapply(tree: Tree): Option[String] = tree match {
      case Literal(Constant(str: String)) =>
        Some(str)
      case _ =>
        None
    }
  }

  // extractor that matches compiler-generated applications of static implicit conversions
  object ImplicitlyConverted {
    def unapply(tree: Tree): Option[(Tree, Tree)] = tree match {
      case Apply(fun, List(prefix))
        if isGlobalImplicitConversion(fun) && (tree.pos == NoPosition || prefix.pos == NoPosition || tree.pos == prefix.pos) =>
        Some((prefix, fun))
      case _ =>
        None
    }
  }

  object NewInstance {
    def unapply(tree: Tree): Option[(Tree, List[Tree])] = tree match {
      case Apply(Select(New(tpeTree), termNames.CONSTRUCTOR), args) =>
        Some((tpeTree, args))
      case _ =>
        None
    }
  }

  object ScexMultiApply {
    def apply(qual: Tree, valueArgLists: List[List[Tree]]): Tree =
      valueArgLists.foldLeft(qual)(Apply(_, _))

    def unapply(tree: Tree): Option[(Tree, List[List[Tree]])] = tree match {
      case Apply(ScexMultiApply(qual, argLists), args) => Some((qual, argLists :+ args))
      case _ => Some((tree, Nil))
    }
  }

  object MemberCall {
    def apply(qual: Tree, name: Name, typeArgs: List[Tree], valueArgLists: List[List[Tree]]): Tree = {
      val selected = Select(qual, name)
      val typeApplied = if (typeArgs.nonEmpty) TypeApply(selected, typeArgs) else selected
      valueArgLists.foldLeft(typeApplied)(Apply(_, _))
    }

    def unapply(tree: Tree): Option[(Tree, Name, List[Tree], List[List[Tree]])] = {
      val ScexMultiApply(qual, argLists) = tree
      val (typeQual, typeArgs) = qual match {
        case TypeApply(tq, ta) => (tq, ta)
        case _ => (qual, Nil)
      }
      typeQual match {
        case Select(squal, name) => Some((squal, name, typeArgs, argLists))
        case _ => None
      }
    }
  }

  object VariableIdent {
    def unapply(tree: Tree): Option[String] = stripInferredTrees(tree) match {
      case SelectDynamic(SyntacticIdent(TermName("_vars")), name) => Some(name)
      case _ => None
    }
  }

  object SyntacticSelect {
    def unapply(tree: Tree): Option[(Tree, String)] = stripInferredTrees(tree) match {
      case Select(qual, name) =>
        Some((qual, name.decodedName.toString))
      case SelectDynamic(qual, name) =>
        Some((qual, name))
      case _ => None
    }
  }

  object SyntacticIdent {
    def unapply(tree: Tree): Option[Name] = stripInferredTrees(tree) match {
      case Ident(name) =>
        Some(name)
      case Select(qual, name) if qual.pos.start == qual.pos.end && qual.pos.start == tree.pos.start =>
        Some(name)
      case _ => None
    }
  }

  object SelectDynamic {
    def unapply(tree: Tree): Option[(Tree, String)] = tree match {
      case Apply(Select(qual, TermName("selectDynamic")), List(lit@Literal(Constant(name: String))))
        if qual.tpe != null && qual.tpe <:< dynamicTpe && lit.pos.isTransparent =>
        Some((qual, name))
      case _ => None
    }
  }

  object StringInterpolation {
    def unapply(tree: Apply): Option[(List[Tree], List[Tree])] = tree match {
      case Apply(Select(StringContextApply(parts), _), args) => Some((parts, args))
      case _ => None
    }
  }

  object StringContextTree {
    def unapply(tree: Tree): Boolean = tree match {
      case Ident(name) if name.decodedName.toString == "StringContext" => true
      case Select(_, name) if name.decodedName.toString == "StringContext" => true
      case _ => false
    }
  }

  object StringContextApply {
    def unapply(tree: Tree): Option[List[Tree]] = tree match {
      case Apply(Select(StringContextTree(), name), parts) if name.decodedName.toString == "apply" => Some(parts)
      case Apply(StringContextTree(), parts) => Some(parts)
      case _ => None
    }
  }

  def isProperPosition(pos: Position): Boolean =
    pos != null && pos != NoPosition

  def isModuleOrPackage(symbol: Symbol): Boolean = symbol != null &&
    (symbol.isModule || symbol.isModuleClass || symbol.isPackage || symbol.isPackageClass)

  def isJavaField(symbol: Symbol): Boolean =
    symbol != null && symbol.isJava && symbol.isTerm && !symbol.isMethod && !isModuleOrPackage(symbol)

  def isConstructor(s: Symbol): Boolean =
    s.isMethod && s.asMethod.isConstructor

  def memberSignature(s: Symbol): String =
    if (s != null) s"${s.fullName}${paramsSignature(s)}" else null

  def paramsSignature(s: Symbol): String =
    s.info.paramLists.map(_.map(_.typeSignature.toString).mkString("(", ",", ")")).mkString

  def erasureFullName(tpe: Type): String =
    tpe.erasure.typeSymbol.fullName

  def isStableTerm(s: Symbol): Boolean =
    s.isTerm && s.asTerm.isStable

  def stripTypeApply(tree: Tree): Tree = tree match {
    case TypeApply(prefix, _) => stripTypeApply(prefix)
    case _ => tree
  }

  def sameRange(pos1: Position, pos2: Position): Boolean =
    pos1.start == pos2.start && pos1.end == pos2.end

  def stripInferredTrees(tree: Tree): Tree = tree match {
    case Select(qual, _) if sameRange(tree.pos, qual.pos) => stripInferredTrees(qual)
    case Apply(fun, _) if sameRange(tree.pos, fun.pos) => stripInferredTrees(fun)
    case TypeApply(fun, _) if sameRange(tree.pos, fun.pos) => stripInferredTrees(fun)
    case _ => tree
  }

  def paramsOf(tpe: Type): (List[List[Symbol]], List[Symbol]) = tpe match {
    case PolyType(tp, resultType) =>
      paramsOf(resultType)
    case MethodType(params, resultType) =>
      val (moreParams, implParams) = paramsOf(resultType)
      if (params.nonEmpty && params.head.isImplicit)
        (moreParams, params ::: implParams)
      else
        (params :: moreParams, implParams)
    case _ => (Nil, Nil)
  }

  def path(tree: Tree): String = tree match {
    case Select(prefix, name) => s"${path(prefix)}.$name"
    case _: Ident | _: This => tree.symbol.fullName
    case EmptyTree => "<none>"
    case _ => throw new IllegalArgumentException("This tree does not represent simple path: " + showRaw(tree))
  }

  /**
    * Is this tree a path that starts with package and goes through stable symbols (vals and objects)?
    *
    * @return
    */
  def isStableGlobalPath(tree: Tree): Boolean = tree match {
    case Select(prefix, _) => isStableTerm(tree.symbol) && isStableGlobalPath(prefix)
    case Ident(_) => tree.symbol.isStatic && isStableTerm(tree.symbol)
    case This(_) => tree.symbol.isPackageClass
    case _ => false
  }

  def isGlobalImplicitConversion(tree: Tree): Boolean = tree match {
    case TypeApply(prefix, _) => isGlobalImplicitConversion(prefix)
    //TODO handle apply method on implicit function values
    case Select(prefix, name) =>
      tree.symbol.isMethod && tree.symbol.isImplicit && isStableGlobalPath(prefix)
    case _ => false
  }

  // https://groups.google.com/forum/#!topic/scala-user/IeD2siVXyss
  def fixOverride(s: Symbol): Symbol =
    if (s.isTerm && s.asTerm.isOverloaded) {
      s.alternatives.filterNot(_.isSynthetic).head
    } else s

  def withOverrides(s: Symbol): List[Symbol] =
    s :: s.overrides.map(fixOverride)

  def isStaticModule(symbol: Symbol): Boolean =
    symbol != null && symbol.isModule && symbol.isStatic

  def isFromToplevelType(symbol: Symbol): Boolean =
    withOverrides(symbol).exists(toplevelSymbols contains _.owner)

  def isJavaParameterlessMethod(symbol: Symbol): Boolean =
    symbol != null && symbol.isPublic && symbol.isJava && symbol.isMethod &&
      symbol.asMethod.paramLists == List(List()) && !symbol.typeSignature.takesTypeArgs

  def isJavaStaticType(tpe: Type): Boolean = {
    val symbol = tpe.typeSymbol
    symbol != null && symbol.isJava && isModuleOrPackage(symbol)
  }

  def isJavaClass(symbol: Symbol): Boolean =
    symbol.isJava && symbol.isClass && !symbol.isModuleClass && !symbol.isPackageClass

  def isStaticOrConstructor(symbol: Symbol): Boolean =
    symbol.isStatic || (symbol.isMethod && symbol.asMethod.isConstructor)

  def reifyOption[A](opt: Option[A], innerReify: A => Tree): Tree = opt match {
    case Some(x) => q"_root_.scala.Some(${innerReify(x)})"
    case None => q"_root_.scala.None"
  }

  def isBooleanType(tpe: Type): Boolean =
    tpe <:< booleanTpe || tpe <:< jBooleanTpe

  def isGetClass(symbol: Symbol): Boolean =
    symbol.name == TermName("getClass") && withOverrides(symbol).contains(getClassSymbol)

  def isBeanGetter(symbol: Symbol): Boolean = symbol.isMethod && {
    val methodSymbol = symbol.asMethod
    val name = symbol.name.decodedName.toString

    !isGetClass(methodSymbol) && methodSymbol.paramLists == List(List()) && methodSymbol.typeParams.isEmpty &&
      (BeanGetterNamePattern.pattern.matcher(name).matches ||
        BooleanBeanGetterNamePattern.pattern.matcher(name).matches && isBooleanType(methodSymbol.returnType))
  }

  def isParameterless(s: TermSymbol): Boolean =
    !s.isMethod || {
      val paramss = s.asMethod.paramLists
      paramss == Nil || paramss == List(Nil)
    }

  def methodTypesMatch(originalTpe: Type, implicitTpe: Type): Boolean = {
    def paramsMatch(origParams: List[Symbol], implParams: List[Symbol]): Boolean =
      (origParams, implParams) match {
        case (origHead :: origTail, implHead :: implTail) =>
          implHead.typeSignature <:< origHead.typeSignature && paramsMatch(origTail, implTail)
        case (Nil, Nil) => true
        case _ => false
      }

    (originalTpe, implicitTpe) match {
      case (MethodType(origParams, origResultType), MethodType(implParams, implResultType)) =>
        paramsMatch(origParams, implParams) && methodTypesMatch(origResultType, implResultType)
      case (MethodType(_, _), _) | (_, MethodType(_, _)) => false
      case (_, _) => true
    }
  }

  def takesSingleParameter(symbol: MethodSymbol): Boolean =
    symbol.paramLists match {
      case List(List(_)) => true
      case _ => false
    }

  def isBeanSetter(symbol: Symbol): Boolean =
    symbol.isMethod && {
      val methodSymbol = symbol.asMethod
      val name = symbol.name.decodedName.toString

      takesSingleParameter(methodSymbol) && methodSymbol.typeParams.isEmpty &&
        methodSymbol.returnType =:= typeOf[Unit] &&
        BeanSetterNamePattern.pattern.matcher(name).matches
    }

  /**
    * Accessible members include methods, modules, val/var setters and getters and Java fields.
    */
  def accessibleMembers(tpe: Type): List[TermSymbol] =
    tpe.members.toList.collect { case s if s.isPublic && s.isTerm &&
      (s.isJava || (!s.asTerm.isVal && !s.asTerm.isVar)) && !s.isImplementationArtifact => s.asTerm
    }

  def hasType(tree: Tree, tpe: Type): Boolean =
    tree.tpe <:< tpe

  def toStringSymbol(tpe: Type): Symbol =
    symAlternatives(tpe.member(TermName("toString")))
      .find(s => s.isTerm && isParameterless(s.asTerm))
      .getOrElse(NoSymbol)

  def symAlternatives(sym: Symbol): List[Symbol] = sym match {
    case termSymbol: TermSymbol => termSymbol.alternatives
    case NoSymbol => Nil
    case _ => List(sym)
  }

  def isExpressionUtil(symbol: Symbol): Boolean =
    symbol != null && symbol != NoSymbol &&
      (nonBottomSymbolType(symbol) <:< expressionUtilType || isExpressionUtil(symbol.owner))

  def isProfileObject(symbol: Symbol): Boolean =
    nonBottomSymbolType(symbol) <:< profileObjectType

  def isFromProfileObject(symbol: Symbol): Boolean =
    symbol != null && symbol != NoSymbol &&
      (isProfileObject(symbol) || isFromProfileObject(symbol.owner))

  def isScexSynthetic(symbol: Symbol): Boolean =
    symbol != null && symbol != NoSymbol &&
      (nonBottomSymbolType(symbol) <:< syntheticType || isScexSynthetic(symbol.owner))

  def isAdapter(tpe: Type): Boolean =
    tpe != null && !isBottom(tpe) && tpe <:< adapterType

  def isBottom(tpe: Type): Boolean =
    tpe <:< definitions.NullTpe || tpe <:< definitions.NothingTpe

  /**
    * Is this symbol the 'wrapped' field of Java getter adapter?
    */
  def isAdapterWrappedMember(symbol: Symbol): Boolean =
    if (symbol != null && symbol.isTerm) {
      val ts = symbol.asTerm
      if (ts.isGetter)
        ts.name == AdapterWrappedName && ts.owner.isType && isAdapter(ts.owner.asType.toType)
      else
        ts.isVal && ts.getter.isMethod && isAdapterWrappedMember(ts.getter)
    } else false

  def isRootAdapter(tpe: Type): Boolean =
    tpe != null && isAnnotatedWith(tpe.widen, rootAdapterAnnotType)

  def isAnnotatedWith(tpe: Type, annotTpe: Type): Boolean = tpe match {
    case AnnotatedType(annots, underlying) =>
      annots.exists(_.tree.tpe <:< annotTpe) || isAnnotatedWith(underlying, annotTpe)
    case ExistentialType(_, underlying) =>
      isAnnotatedWith(underlying, annotTpe)
    case _ => false
  }

  // gets Java getter called by implicit wrapper
  def getJavaGetter(symbol: Symbol, javaTpe: Type): Symbol = {
    val getterName = "get" + symbol.name.toString.capitalize
    val booleanGetterName = "is" + symbol.name.toString.capitalize

    def fail = throw new Exception(s"Could not find Java getter for property ${symbol.name} on $javaTpe")

    def findGetter(getterName: String) =
      symAlternatives(javaTpe.member(TermName(getterName))).find(isBeanGetter)

    if (isBooleanType(symbol.asMethod.returnType)) {
      findGetter(booleanGetterName) orElse findGetter(getterName) getOrElse fail
    } else {
      findGetter(getterName) getOrElse fail
    }
  }

  def symbolType(symbol: Symbol): Type =
    if (symbol == null) NoType
    else if (symbol.isType) symbol.asType.toType
    else symbol.typeSignature

  def nonBottomSymbolType(symbol: Symbol): Type = {
    val tpe = symbolType(symbol)
    if (tpe <:< definitions.NullTpe || tpe <:< definitions.NothingTpe) NoType else tpe
  }

  def isAdapterConversion(symbol: Symbol): Boolean =
    isProfileObject(symbol.owner) && symbol.isImplicit && symbol.isMethod && isAdapter(symbol.asMethod.returnType)

  def annotations(sym: Symbol): List[Annotation] = {
    sym.info // force annotations
    sym.annotations ++ (if (sym.isTerm) {
      val tsym = sym.asTerm
      if (tsym.isGetter) annotations(tsym.accessed) else Nil
    } else Nil)
  }

  def annotationsIncludingOverrides(sym: Symbol): List[Annotation] =
    withOverrides(sym).flatMap(annotations)

  def debugTree(pref: String, tree: Tree): Unit = {
    println(pref)
    tree.foreach { t =>
      println(show(t.pos).padTo(15, ' ') + ("" + t.tpe).padTo(50, ' ') + show(t))
    }
    println()
  }
}

object MacroUtils {
  def apply(u: Universe): MacroUtils {val universe: u.type} =
    new MacroUtils {
      val universe: u.type = u
    }
}
